package org.fdroid.fdroid.data;

import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.fdroid.fdroid.IndexUpdater;
import org.fdroid.fdroid.IndexV1Updater;
import org.fdroid.fdroid.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {LOLLIPOP_MR1})
public class IndexUpdaterTest {
    private static final String FDROID_CERT = "3082035e30820246a00302010202044c49cd00300d06092a864886f70d01010505003071310b300906035504061302554b3110300e06035504081307556e6b6e6f776e3111300f0603550407130857657468657262793110300e060355040a1307556e6b6e6f776e3110300e060355040b1307556e6b6e6f776e311930170603550403131043696172616e2047756c746e69656b73301e170d3130303732333137313032345a170d3337313230383137313032345a3071310b300906035504061302554b3110300e06035504081307556e6b6e6f776e3111300f0603550407130857657468657262793110300e060355040a1307556e6b6e6f776e3110300e060355040b1307556e6b6e6f776e311930170603550403131043696172616e2047756c746e69656b7330820122300d06092a864886f70d01010105000382010f003082010a028201010096d075e47c014e7822c89fd67f795d23203e2a8843f53ba4e6b1bf5f2fd0e225938267cfcae7fbf4fe596346afbaf4070fdb91f66fbcdf2348a3d92430502824f80517b156fab00809bdc8e631bfa9afd42d9045ab5fd6d28d9e140afc1300917b19b7c6c4df4a494cf1f7cb4a63c80d734265d735af9e4f09455f427aa65a53563f87b336ca2c19d244fcbba617ba0b19e56ed34afe0b253ab91e2fdb1271f1b9e3c3232027ed8862a112f0706e234cf236914b939bcf959821ecb2a6c18057e070de3428046d94b175e1d89bd795e535499a091f5bc65a79d539a8d43891ec504058acb28c08393b5718b57600a211e803f4a634e5c57f25b9b8c4422c6fd90203010001300d06092a864886f70d0101050500038201010008e4ef699e9807677ff56753da73efb2390d5ae2c17e4db691d5df7a7b60fc071ae509c5414be7d5da74df2811e83d3668c4a0b1abc84b9fa7d96b4cdf30bba68517ad2a93e233b042972ac0553a4801c9ebe07bf57ebe9a3b3d6d663965260e50f3b8f46db0531761e60340a2bddc3426098397fda54044a17e5244549f9869b460ca5e6e216b6f6a2db0580b480ca2afe6ec6b46eedacfa4aa45038809ece0c5978653d6c85f678e7f5a2156d1bedd8117751e64a4b0dcd140f3040b021821a8d93aed8d01ba36db6c82372211fed714d9a32607038cdfd565bd529ffc637212aaa2c224ef22b603eccefb5bf1e085c191d4b24fe742b17ab3f55d4e6f05ef";

    protected ContentResolver contentResolver;
    protected ContextWrapper context;

    @Before
    public final void setupBase() {
        contentResolver = ApplicationProvider.getApplicationContext().getContentResolver();
        context = TestUtils.createContextWithContentResolver(contentResolver);
        TestUtils.registerContentProvider(AppProvider.getAuthority(), AppProvider.class);
    }

    @Test
    public void processIndex() throws IOException, IndexUpdater.UpdateException {
        List<Repo> repos = RepoProvider.Helper.all(context);
        for (Repo repo : repos) {
            RepoProvider.Helper.remove(context, repo.getId());
        }
        File index = TestUtils.copyResourceToTempFile("index-v1_20200916.jar");
        Repo repo = createRepo("F-Droid", "https://f-droid.org/repo", context, FDROID_CERT);
        IndexV1Updater updater = new IndexV1Updater(context, repo);
        updater.processDownloadedIndex(index, "");
        assertThat(AppProvider.Helper.all(context.getContentResolver()).size()).isEqualTo(3120);
    }

    /**
     * Creates a real instance of {@code Repo} by loading it from the database,
     * that ensures it includes the primary key from the database.
     */
    static Repo createRepo(String name, String uri, Context context, String signingCert) {
        ContentValues values = new ContentValues(3);
        values.put(Schema.RepoTable.Cols.SIGNING_CERT, signingCert);
        values.put(Schema.RepoTable.Cols.ADDRESS, uri);
        values.put(Schema.RepoTable.Cols.NAME, name);
        RepoProvider.Helper.insert(context, values);
        return RepoProvider.Helper.findByAddress(context, uri);
    }
}
