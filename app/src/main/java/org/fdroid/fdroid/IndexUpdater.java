/*
 * Copyright (C) 2018 Senecto Limited
 * Copyright (C) 2016 Blue Jay Wireless
 * Copyright (C) 2015-2016 Daniel Martí <mvdan@mvdan.cc>
 * Copyright (C) 2014-2018 Hans-Christoph Steiner <hans@eds.org>
 * Copyright (C) 2014-2016 Peter Serwylo <peter@serwylo.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.fdroid.fdroid;

import android.content.Context;
import androidx.annotation.NonNull;

import org.fdroid.fdroid.data.Repo;

import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.jar.JarEntry;

/**
 * Updates the local database with a repository's app/apk metadata and verifying
 * the JAR signature on the file received from the repository. As an overview:
 * <ul>
 * <li>Download the {@code index.jar}
 * <li>Verify that it is signed correctly and by the correct certificate
 * <li>Parse the {@code index.xml} that is in {@code index.jar}
 * <li>Save the resulting repo, apps, and apks to the database.
 * <li>Process any push install/uninstall requests included in the repository
 * </ul>
 * <b>WARNING</b>: this class is the central piece of the entire security model of
 * FDroid!  Avoid modifying it when possible, if you absolutely must, be very,
 * very careful with the changes that you are making!
 */
public abstract class IndexUpdater {
    private static final String TAG = "IndexUpdater";

    final String indexUrl;

    @NonNull
    final Context context;
    @NonNull
    final Repo repo;
    boolean hasChanged;


    /**
     * Updates an app repo as read out of the database into a {@link Repo} instance.
     *
     * @param repo A {@link Repo} read out of the local database
     */
    public IndexUpdater(@NonNull Context context, @NonNull Repo repo) {
        this.context = context;
        this.repo = repo;
        this.indexUrl = getIndexUrl(repo);
    }

    protected abstract String getIndexUrl(@NonNull Repo repo);

    boolean hasChanged() {
        return hasChanged;
    }

    final ProgressListener downloadListener = new ProgressListener() {
        @Override
        public void onProgress(String urlString, long bytesRead, long totalBytes) {
            UpdateService.reportDownloadProgress(context, IndexUpdater.this, bytesRead, totalBytes);
        }
    };

    protected final ProgressListener processIndexListener = new ProgressListener() {
        @Override
        public void onProgress(String urlString, long bytesRead, long totalBytes) {
            UpdateService.reportProcessIndexProgress(context, IndexUpdater.this, bytesRead, totalBytes);
        }
    };

    void notifyProcessingApps(int appsSaved, int totalApps) {
        UpdateService.reportProcessingAppsProgress(context, this, appsSaved, totalApps);
    }

    void notifyCommittingToDb() {
        notifyProcessingApps(0, -1);
    }


    public static class UpdateException extends Exception {

        private static final long serialVersionUID = -4492452418826132803L;

        public UpdateException(String message) {
            super(message);
        }

        public UpdateException(String message, Exception cause) {
            super(message, cause);
        }
    }

    public static class SigningException extends UpdateException {
        SigningException(String message) {
            super("Repository was not signed correctly: " + message);
        }

        SigningException(Repo repo, String message) {
            super((repo == null ? "Repository" : repo.name) + " was not signed correctly: " + message);
        }
    }

    /**
     * FDroid's index.jar is signed using a particular format and does not allow lots of
     * signing setups that would be valid for a regular jar.  This validates those
     * restrictions.
     */
    static X509Certificate getSigningCertFromJar(JarEntry jarEntry) throws SigningException {
        final CodeSigner[] codeSigners = jarEntry.getCodeSigners();
        if (codeSigners == null || codeSigners.length == 0) {
            throw new SigningException("No signature found in index");
        }
        /* we could in theory support more than 1, but as of now we do not */
        if (codeSigners.length > 1) {
            throw new SigningException("index-v1.jar must be signed by a single code signer!");
        }
        List<? extends Certificate> certs = codeSigners[0].getSignerCertPath().getCertificates();
        if (certs.size() != 1) {
            throw new SigningException("index-v1.jar code signers must only have a single certificate!");
        }
        return (X509Certificate) certs.get(0);
    }
}
