package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** M6: ban debug ban giao chi co mot icon chinh, khong xin quyen nguy hiem. */
@RunWith(AndroidJUnit4.class)
public class M6ManifestContractTest {
    @Test public void debugBuildHasExactlyOneLauncherAndKeepsTheStorageTestHost() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager manager = context.getPackageManager();
        Intent launchers = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.getPackageName());
        List<ResolveInfo> matches = manager.queryIntentActivities(launchers, 0);
        assertEquals("The handoff APK must not create a second Storage Test icon", 1, matches.size());
        assertEquals("vn.edu.usth.covidstats.MainActivity", matches.get(0).activityInfo.name);
        assertNotNull(manager.getActivityInfo(new android.content.ComponentName(context,
                "vn.edu.usth.covidstats.debug.StoragePreviewActivity"), 0));
    }

    @Test public void applicationUsesNetworkPermissionsWithoutDangerousPermissions() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager manager = context.getPackageManager();
        PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertNotNull(info.requestedPermissions);
        List<String> permissions = Arrays.asList(info.requestedPermissions);
        assertTrue(permissions.contains(android.Manifest.permission.INTERNET));
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_NETWORK_STATE));
        for (String name : permissions) {
            PermissionInfo permission = manager.getPermissionInfo(name, 0);
            assertNotEquals("Unexpected dangerous permission: " + name,
                    PermissionInfo.PROTECTION_DANGEROUS,
                    permission.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
        }
    }
}
