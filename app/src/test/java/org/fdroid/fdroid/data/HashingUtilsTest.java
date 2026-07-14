package org.fdroid.fdroid.data;

import org.fdroid.fdroid.HashingUtils;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

public class HashingUtilsTest {
    @Test
    public void testHexMixedCase() throws Exception {
        assertThat(HashingUtils.hex(HashingUtils.unhex("ABcdEF123456"))).matches("abcdef123456");
    }

    @Test
    public void testFileHashNullCases() throws Exception {
        assertThat(HashingUtils.isFileMatchingHash(new File(""), null, "sha256")).isFalse();
        assertThat(HashingUtils.isFileMatchingHash(new File("/tmp/abc"), null, "sha256")).isFalse();
        assertThat(HashingUtils.isFileMatchingHash(new File("/tmp/abc"), "null", "sha256")).isFalse();

        byte[] bytes = "F-Droid Classic".getBytes(StandardCharsets.UTF_8);
        File file = File.createTempFile("hashfile", ".tmp");
        write(bytes, file);
        assertThat(HashingUtils.isFileMatchingHash(file, "", "sha256")).isFalse();
        assertThat(HashingUtils.isFileMatchingHash(file, null, "sha256")).isFalse();
        assertThrows(RuntimeException.class, () -> HashingUtils.isFileMatchingHash(
                file,
                "533d66dd246976b283514aa74a712862a9bfc493f8cb375ecc86422a6b34f4e8",
                "FDROID"));
    }

    @Test
    public void testFileMatchingHash() throws Exception {
        byte[] bytes = "F-Droid Classic".getBytes(StandardCharsets.UTF_8);
        File file = File.createTempFile("hashfile", ".tmp");
        write(bytes, file);
        assertThat(HashingUtils.isFileMatchingHash(file,
                "533d66dd246976b283514aa74a712862a9bfc493f8cb375ecc86422a6b34f4e8",
                "sha256")).isTrue();
    }

    @Test
    public void testFileMatchingHashSha512() throws Exception {
        byte[] bytes = "F-Droid Classic".getBytes(StandardCharsets.UTF_8);
        File file = File.createTempFile("hashfile", ".tmp");
        write(bytes, file);
        assertThat(HashingUtils.isFileMatchingHash(file,
                "533d66dd246976b283514aa74a712862a9bfc493f8cb375ecc86422a6b34f4e8",
                "sha512")).isFalse();

        assertThat(HashingUtils.isFileMatchingHash(file,
                "facb4f2432acdf11178159af6cb17bb253aa25a3a1f5962cf49265250310adfb643f3c121247db7297ebf06d0831971b56ca10e6c34422e3aeb58f4a0439179c",
                "sha512")).isTrue();
    }
}
