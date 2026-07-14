package org.fdroid.fdroid.data;

import android.net.Uri;

import org.fdroid.fdroid.R;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;


public class NewRepoConfigTest {
    @Test
    public void basicValidRepoTest() {
        final Uri repoUrl = Uri.parse("https://bubu1.eu/cctg/fdroid/repo");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUri()).isEqualTo(repoUrl);
        assertThat(newRepoConfig.getFingerprint()).isNull();
        assertThat(newRepoConfig.getPassword()).isNull();
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getHost()).isEqualTo("bubu1.eu");
        assertThat(newRepoConfig.getPort()).isEqualTo(443);
    }

    @Test
    public void stripTrailingSlash() {
        final Uri repoUrl = Uri.parse("https://bubu1.eu/cctg/fdroid/repo/");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isNull();
        assertThat(newRepoConfig.getPassword()).isNull();
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getHost()).isEqualTo("bubu1.eu");
        assertThat(newRepoConfig.getPort()).isEqualTo(443);
    }

    @Test
    public void stripTrailingSlashWithFingerprintPresent() {
        final Uri repoUrl = Uri.parse("https://bubu1.eu/cctg/fdroid/repo/?fingerprint=abc");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("abc");
        assertThat(newRepoConfig.getPassword()).isNull();
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getHost()).isEqualTo("bubu1.eu");
        assertThat(newRepoConfig.getPort()).isEqualTo(443);
    }

    @Test
    public void httpValidRepoTest() {
        final Uri repoUrl = Uri.parse("http://bubu1.eu/cctg/fdroid/repo");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUri()).isEqualTo(repoUrl);
        assertThat(newRepoConfig.getFingerprint()).isNull();
        assertThat(newRepoConfig.getPassword()).isNull();
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getHost()).isEqualTo("bubu1.eu");
        assertThat(newRepoConfig.getPort()).isEqualTo(80);
    }

    @Test
    public void stripFragmentWithFingerprintTest(){
        final Uri repoUrl = Uri.parse("https://bubu1.eu/cctg/fdroid/repo?fingerprint=ABC#SomeUniqueId");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("ABC");
    }


    @Test
    public void stripFragmentTest(){
        final Uri repoUrl = Uri.parse("https://bubu1.eu/cctg/fdroid/repo#SomeUniqueId");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
    }

    @Test
    public void junkWithUrlTest(){
        final Uri repoUrl = Uri.parse("Thisisanalternative F-Droid clienthttps://bubu1.eu/cctg/fdroid/repo");
        assertThat(repoUrl.toString()).isEqualTo("Thisisanalternative F-Droid clienthttps://bubu1.eu/cctg/fdroid/repo");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isFalse();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(R.string.repo_url_invalid);
    }

    @Test
    public void noSchemeRepoTest() {
        final Uri repoUrl = Uri.parse("bubu1.eu");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isFalse();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(R.string.repo_url_invalid);
    }

    @Test
    public void fdroidreposRepoWithFingerprint(){
        final Uri repoUrl = Uri.parse("fdroidrepos://bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
    }

    @Test
    public void fdroidrepoRepoWithFingerprint(){
        final Uri repoUrl = Uri.parse("fdroidrepo://bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("http://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
    }

    @Test
    public void repoWithUserinfo(){
        final Uri repoUrl = Uri.parse("https://user:password@bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        assertThat(newRepoConfig.getUsername()).isEqualTo("user");
        assertThat(newRepoConfig.getPassword()).isEqualTo("password");
    }

    @Test
    public void repoWithEmptyUserInfo(){
        final Uri repoUrl = Uri.parse("https://@bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getPassword()).isNull();
    }

    @Test
    public void repoWithEmptyPassword(){
        final Uri repoUrl = Uri.parse("https://user:@bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        // this is actually  an empty password... but this seems not useful so we filter it out instead as well.
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getPassword()).isNull();
    }

    @Test
    public void repoWithOnlyUser(){
        final Uri repoUrl = Uri.parse("https://user@bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
        assertThat(newRepoConfig.getUsername()).isNull();
        assertThat(newRepoConfig.getPassword()).isNull();
    }

    @Test
    public void repoWithMoreQueryParms(){
        final Uri repoUrl = Uri.parse("https://bubu1.eu/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec&bssid=jksjfhsdkj&ssid=jsfjlkjds");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
    }

    @Test
    public void repoWithExplicitPort(){
        final Uri repoUrl = Uri.parse("https://bubu1.eu:8080/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec&bssid=jksjfhsdkj&ssid=jsfjlkjds");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getPort()).isEqualTo(8080);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu:8080/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
    }

    @Test
    public void repoWithExplicitPortAndUserinfo(){
        final Uri repoUrl = Uri.parse("https://bubu:bubu1@bubu1.eu:8080/cctg/fdroid/repo/?fingerprint=f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec&bssid=jksjfhsdkj&ssid=jsfjlkjds");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getPort()).isEqualTo(8080);
        assertThat(newRepoConfig.getUsername()).isEqualTo("bubu");
        assertThat(newRepoConfig.getPassword()).isEqualTo("bubu1");
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu:8080/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
    }

    @Test
    public void repoWithALLCAPS(){
        final Uri repoUrl = Uri.parse("HTTPS://BUBU1.EU/CCTG/FDROID/REPO?FINGERPRINT=F3F30B6D212D84AEA604C3DF00E9E4D4A39194A33BF6EC58DB53AF0AC4B41BEC");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getPort()).isEqualTo(443);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isEqualTo("f3f30b6d212d84aea604c3df00e9e4d4a39194a33bf6ec58db53af0ac4b41bec");
    }

    @Test
    public void repoWithALLCAPSNoFingerprint(){
        final Uri repoUrl = Uri.parse("HTTPS://BUBU1.EU/CCTG/FDROID/REPO");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getPort()).isEqualTo(443);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/cctg/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isNull();
    }
    @Test
    public void repoWithHostAndPathALLCAPS(){
        // if the /FDROID/REPO part if all caps everything gets downcased. This is probably fine.
        final Uri repoUrl = Uri.parse("https://BUBU1.EU/CCTG/fdroid/repo");
        final NewRepoConfig newRepoConfig = new NewRepoConfig(repoUrl);
        assertThat(newRepoConfig.isValidRepo()).isTrue();
        assertThat(newRepoConfig.getErrorMessage()).isEqualTo(0);
        assertThat(newRepoConfig.getPort()).isEqualTo(443);
        assertThat(newRepoConfig.getRepoUriString()).isEqualTo("https://bubu1.eu/CCTG/fdroid/repo");
        assertThat(newRepoConfig.getFingerprint()).isNull();
    }
}
