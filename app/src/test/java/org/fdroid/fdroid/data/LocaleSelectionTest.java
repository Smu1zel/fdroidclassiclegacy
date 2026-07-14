package org.fdroid.fdroid.data;


import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.os.Build;

import org.fdroid.fdroid.TestUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class LocaleSelectionTest {

    @Test
    public void correctLocaleSelectionBeforeSDK24() throws Exception {
        TestUtils.setFinalStatic(Build.VERSION.class.getDeclaredField("SDK_INT"), 23);
        assertThat(Build.VERSION.SDK_INT).isLessThan(24);
        App app;

        Map<String, Map<String, Object>> localized = new HashMap<>();
        HashMap<String, Object> en_US = new HashMap<>();
        en_US.put("summary", "summary-en_US");
        HashMap<String, Object> de_AT = new HashMap<>();
        de_AT.put("summary", "summary-de_AT");
        HashMap<String, Object> de_DE = new HashMap<>();
        de_DE.put("summary", "summary-de_DE");
        HashMap<String, Object> sv = new HashMap<>();
        sv.put("summary", "summary-sv");
        HashMap<String, Object> sv_FI = new HashMap<>();
        sv_FI.put("summary", "summary-sv_FI");

        localized.put("de-AT", de_AT);
        localized.put("de-DE", de_DE);
        localized.put("en-US", en_US);
        localized.put("sv", sv);
        localized.put("sv-FI", sv_FI);

        // Easy mode. en-US metadata with an en-US locale
        Locale.setDefault(new Locale("en", "US"));
        app = new App();
        app.setLocalized(localized);
        assertThat(app.summary).matches("summary-en_US");

        // Fall back to en-US locale, when we have a different en locale
        Locale.setDefault(new Locale("en", "UK"));
        app = new App();
        app.setLocalized(localized);
        assertThat(app.summary).matches("summary-en_US");

        // Fall back to language only
        Locale.setDefault(new Locale("en", "UK"));
        app = new App();
        app.setLocalized(localized);
        assertThat(app.summary).matches("summary-en_US");

        // select the correct one out of multiple language locales
        Locale.setDefault(new Locale("de", "DE"));
        app = new App();
        app.setLocalized(localized);
        assertThat(app.summary).matches("summary-de_DE");

        // Even when we have a non-exact matching locale, we should fall back to the same language
        // TODO: We really should be falling back to de-DE herem but we currently don't
        Locale.setDefault(new Locale("de", "CH"));
        app = new App();
        app.setLocalized(localized);
        assertThat(app.summary).matches("summary-de_..");

        // Test fallback to base lang with not exact matching locale
        Locale.setDefault(new Locale("sv", "SE"));
        app = new App();
        app.setLocalized(localized);
        assertThat(app.summary).matches("summary-sv");
    }

    @Test
    public void correctLocaleSelectionFromSDK24() throws Exception {

        TestUtils.setFinalStatic(Build.VERSION.class.getDeclaredField("SDK_INT"), 29);
        assertThat(Build.VERSION.SDK_INT).isAtLeast(24);

        App app = spy(new App());

        // Set both default locale as well as the locale list, becasue the algorithm uses both...
        Locale.setDefault(new Locale("en", "US"));
        doReturn("en-US,de-DE").when(app).getLocales();


        //no metadata present
        Map<String, Map<String, Object>> localized = new HashMap<>();
        app.setLocalized(localized);
        assertThat(app.summary).matches("Unknown application");

        HashMap<String, Object> en_US = new HashMap<>();
        en_US.put("summary", "summary-en_US");
        HashMap<String, Object> en_GB = new HashMap<>();
        en_GB.put("summary", "summary-en_GB");
        HashMap<String, Object> de_AT = new HashMap<>();
        de_AT.put("summary", "summary-de_AT");
        HashMap<String, Object> de_DE = new HashMap<>();
        de_DE.put("summary", "summary-de_DE");

        app.summary = "reset";
        localized.put("de-AT", de_AT);
        localized.put("de-DE", de_DE);
        localized.put("en-US", en_US);
        app.setLocalized(localized);
        // just select the matching en-US locale, nothing special here
        assertThat(app.summary).matches("summary-en_US");

        Locale.setDefault(new Locale("en", "SE"));
        doReturn("en-SE,de-DE").when(app).getLocales();
        app.setLocalized(localized);
        // Fall back to another en locale before de
        assertThat(app.summary).matches("summary-en_US");

        app.summary = "reset";
        localized.clear();
        localized.put("de-AT", de_AT);
        localized.put("de-DE", de_DE);
        localized.put("en-GB", en_GB);
        localized.put("en-US", en_US);

        Locale.setDefault(new Locale("de", "AT"));
        doReturn("de-AT,de-DE").when(app).getLocales();
        app.setLocalized(localized);
        // full match against a non-default locale
        assertThat(app.summary).matches("summary-de_AT");

        app.summary = "reset";
        localized.clear();
        localized.put("de-AT", de_AT);
        localized.put("de", de_DE);
        localized.put("en-GB", en_GB);
        localized.put("en-US", en_US);

        Locale.setDefault(new Locale("de", "CH"));
        doReturn("de-CH,en-US").when(app).getLocales();
        app.setLocalized(localized);
        // TODO: We should fall back to `de` and not another de-XX locale
        assertThat(app.summary).matches("summary-de_..");

        app.summary = "reset";
        localized.clear();
        localized.put("en-GB", en_GB);
        localized.put("en-US", en_US);

        Locale.setDefault(new Locale("en", "AU"));
        doReturn("en-AU").when(app).getLocales();
        app.setLocalized(localized);
        // TODO: Hard mode: en_AU is closer to en_GB than en_US...
        assertThat(app.summary).matches("summary-en_..");

        app.summary = "reset";
        Locale.setDefault(new Locale("zh", "TW", "#Hant"));
        doReturn("zh-Hant-TW,zh-Hans-CN").when(app).getLocales();
        localized.clear();
        localized.put("en", en_GB);
        localized.put("en-US", en_US);
        app.setLocalized(localized);
        //No match at all, fall back to an english locale
        assertThat(app.summary).matches("summary-en_..");

        app.summary = "reset";
        HashMap<String, Object> zh_TW = new HashMap<>();
        de_AT.put("summary", "summary-zh_TW");
        HashMap<String, Object> zh_CN = new HashMap<>();
        de_DE.put("summary", "summary-zh-CN");

        localized.clear();
        localized.put("en-US", en_US);
        localized.put("zh-CN", zh_CN);
        localized.put("zh-TW", zh_TW);
        app.setLocalized(localized);
        //TODO: We should match a chinese locale here...
        //assertThat(app.summary).matches("summary-zh_TW");


        localized.clear();
        localized.put("en-US", en_US);
        localized.put("zh-CN", zh_CN);
        app.setLocalized(localized);
        //TODO: We should match a chinese locale here...
        //assertThat(app.summary).matches("summary-zh_CN");
    }
}
