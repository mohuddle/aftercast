package de.danoeh.antennapod.ui.screen.preferences;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import de.danoeh.antennapod.R;

/**
 * Home-screen icon choices. Each choice is an activity alias of the splash screen,
 * and only one alias is enabled.
 */
public final class AppIcon {
    public static final String TEAL = "teal";
    public static final String EMBER = "ember";
    public static final String NAVY = "navy";
    public static final String LETTERPRESS = "letterpress";
    public static final String DUSK = "dusk";

    private static final String[] KEYS = {TEAL, EMBER, NAVY, LETTERPRESS, DUSK};

    private AppIcon() {
    }

    public static String[] keys() {
        return KEYS.clone();
    }

    public static String normalize(String key) {
        if (key == null) {
            return TEAL;
        }
        for (String known : KEYS) {
            if (known.equals(key)) {
                return known;
            }
        }
        return TEAL;
    }

    public static int preview(String key) {
        switch (normalize(key)) {
            case EMBER:
                return R.mipmap.ic_launcher_ember;
            case NAVY:
                return R.mipmap.ic_launcher_navy;
            case LETTERPRESS:
                return R.mipmap.ic_launcher_letterpress;
            case DUSK:
                return R.mipmap.ic_launcher_dusk;
            case TEAL:
            default:
                return R.mipmap.ic_launcher_teal;
        }
    }

    public static int label(String key) {
        switch (normalize(key)) {
            case EMBER:
                return R.string.app_icon_ember;
            case NAVY:
                return R.string.app_icon_navy;
            case LETTERPRESS:
                return R.string.app_icon_letterpress;
            case DUSK:
                return R.string.app_icon_dusk;
            case TEAL:
            default:
                return R.string.app_icon_teal;
        }
    }

    public static void apply(Context context, String key) {
        String selected = className(normalize(key));
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        packageManager.setComponentEnabledSetting(
                new ComponentName(packageName, selected),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (String candidate : KEYS) {
            String alias = className(candidate);
            if (alias.equals(selected)) {
                continue;
            }
            packageManager.setComponentEnabledSetting(
                    new ComponentName(packageName, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    private static String className(String key) {
        switch (normalize(key)) {
            case EMBER:
                return "de.danoeh.antennapod.LauncherEmber";
            case NAVY:
                return "de.danoeh.antennapod.LauncherNavy";
            case LETTERPRESS:
                return "de.danoeh.antennapod.LauncherLetterpress";
            case DUSK:
                return "de.danoeh.antennapod.LauncherDusk";
            case TEAL:
            default:
                return "de.danoeh.antennapod.LauncherTeal";
        }
    }
}
