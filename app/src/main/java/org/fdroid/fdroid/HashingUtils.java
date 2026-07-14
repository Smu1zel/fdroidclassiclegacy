/*
 * Copyright (C) 2010-2011 Ciaran Gultnieks <ciaran@ciarang.com>
 * Copyright (C) 2011 Henrik Tunedal <tunedal@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.fdroid.fdroid;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class HashingUtils {
    /**
     * Checks the file against the provided hash, returning whether it is a match.
     */
    public static boolean isFileMatchingHash(File file, String targetHash, String hashType) {
        if (!file.exists()) {
            return false;
        }
        if (targetHash == null) {
            return false;
        }
        HashFunction hashFunction;
        switch (hashType) {
            case "sha256":
                hashFunction = Hashing.sha256();
                break;
            case "sha512":
                hashFunction = Hashing.sha512();
                break;
            case "sha384":
                hashFunction = Hashing.sha384();
                break;
            case "sha1":
                hashFunction = Hashing.sha1();
                break;
            case "md5":
                hashFunction = Hashing.md5();
                break;
            default:
                throw new RuntimeException(String.format("HashType %s is unsupported", hashType));
        }
        String calculatedHash;
        try {
            calculatedHash = Files.asByteSource(file).hash(hashFunction).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return calculatedHash.equals(targetHash.toLowerCase(Locale.ENGLISH));
    }

    public static String hex(byte[] sig) {
        return BaseEncoding.base16().lowerCase().encode(sig);
    }

    public static byte[] unhex(String data) {
        return BaseEncoding.base16().decode(data.toUpperCase(Locale.ENGLISH));
    }

}
