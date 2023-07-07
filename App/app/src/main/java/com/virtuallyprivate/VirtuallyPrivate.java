package com.virtuallyprivate;

import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.location.Location;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaSyncEvent;
import android.net.wifi.WifiInfo;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class VirtuallyPrivate implements IXposedHookLoadPackage {
    final static String NAME = "VirtuallyPrivate";

    static Context m_systemContext;
    static XSharedPreferences m_pref;
    static NotificationManager m_notificationsManager;
    private HashMap<String, Boolean> m_isRestricted;  // for cache
    private HashMap<String, XC_MethodHook> m_permissionsHooks;

    private void _initPermissionsHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        m_permissionsHooks = new HashMap<>();
        m_permissionsHooks.put(Permissions.CAMERA, _createReplacementHook(Permissions.CAMERA, lpparam, null));
        m_permissionsHooks.put(SharedPrefs.Identifications.IMEI, _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, m_pref.getString(SharedPrefs.Identifications.IMEI, "")));
        m_permissionsHooks.put(SharedPrefs.Identifications.MEID, _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, m_pref.getString(SharedPrefs.Identifications.MEID, "")));
        m_permissionsHooks.put(SharedPrefs.Identifications.OPERATOR, _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, m_pref.getString(SharedPrefs.Identifications.OPERATOR, "")));
        m_permissionsHooks.put(Permissions.STORAGE_WRITE, _createStorageHook(lpparam, Permissions.STORAGE_WRITE));
        m_permissionsHooks.put(Permissions.STORAGE_READ, _createStorageHook(lpparam, Permissions.STORAGE_READ));
    }

    private void _init(XC_LoadPackage.LoadPackageParam lpparam) {
        m_isRestricted = new HashMap<>();
        m_pref = new XSharedPreferences(MainActivity.class.getPackage().getName(), VirtuallyPrivate.NAME);
        m_systemContext = (Context) XposedHelpers.callMethod(
                XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread",
                        lpparam.classLoader), "currentActivityThread"), "getSystemContext");

        // init notifications
        NotificationChannel notificationsChannel = new NotificationChannel(NAME, "Restrictions", NotificationManager.IMPORTANCE_HIGH);
        m_notificationsManager = (NotificationManager) m_systemContext.getSystemService(Context.NOTIFICATION_SERVICE);
        m_notificationsManager.createNotificationChannel(notificationsChannel);

        _initPermissionsHooks(lpparam);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            return;
        }

        XposedBridge.log("Loaded VirtuallyPrivate");
        _init(lpparam);
        m_pref.reload();
        _hookPermissions(lpparam);
    }

    private void _hookPermissions(XC_LoadPackage.LoadPackageParam lpparam) {
        // Clipboard
        findAndHookMethod(ClipData.class, "getItemAt", int.class, _createReplacementHook(Permissions.CLIPBOARD, lpparam, m_pref.getString(SharedPrefs.CLIPBOARD, "")));

        // Identifications
        this._hookIdentifications(lpparam);

        // App list
        findAndHookMethod("android.app.ApplicationPackageManager", lpparam.classLoader,
                "getInstalledApplications", int.class, _createAppListHook(lpparam, false));
        findAndHookMethod("android.app.ApplicationPackageManager", lpparam.classLoader,
                "getInstalledPackages", int.class, _createAppListHook(lpparam, true));
        findAndHookMethod("android.content.pm.PackageManager", lpparam.classLoader,
                "getInstalledApplications", int.class, _createAppListHook(lpparam, false));
        findAndHookMethod("android.content.pm.PackageManager", lpparam.classLoader,
                "getInstalledPackages", int.class, _createAppListHook(lpparam, true));

        // Camera
        findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class, m_permissionsHooks.get(Permissions.CAMERA));
        findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera", String.class, Executor.class, CameraDevice.StateCallback.class, m_permissionsHooks.get(Permissions.CAMERA));
        findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "getCameraInfo", int.class, Camera.CameraInfo.class, m_permissionsHooks.get(Permissions.CAMERA));
        findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "open", m_permissionsHooks.get(Permissions.CAMERA));
        findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "open", int.class, m_permissionsHooks.get(Permissions.CAMERA));

        // Location
        findAndHookMethod(Location.class, "getLatitude", _createLocationHook(lpparam, SharedPrefs.Location.LATITUDE));
        findAndHookMethod(Location.class, "getLongitude", _createLocationHook(lpparam, SharedPrefs.Location.LONGITUDE));

        // Microphone
        XC_MethodHook mediaHook = this._createReplacementHook(Permissions.MICROPHONE, lpparam, null);
        findAndHookMethod(AudioRecord.class, "startRecording", MediaSyncEvent.class, mediaHook);
        findAndHookMethod(AudioRecord.class, "startRecording", mediaHook);
        findAndHookMethod(MediaRecorder.class, "setAudioSource", int.class, mediaHook);

        // Contact list
        this._hookContentResolverQuery(lpparam, Permissions.CONTACTS_LIST, ContactsContract.Data.CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.CONTACTS_LIST, ContactsContract.DeletedContacts.CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.CONTACTS_LIST, ContactsContract.CommonDataKinds.Contactables.CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.CONTACTS_LIST, ContactsContract.RawContacts.CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.CONTACTS_LIST, ContactsContract.Contacts.CONTENT_URI);

        // Call log
        this._hookContentResolverQuery(lpparam, Permissions.CALL_LOG, CallLog.Calls.CONTENT_URI);

        // Wifi
        findAndHookMethod(WifiInfo.class, "getSSID", _createReplacementHook(Permissions.WIFI, lpparam, m_pref.getString(SharedPrefs.Wifi.SSID, "")));
        findAndHookMethod(WifiInfo.class, "getBSSID", _createReplacementHook(Permissions.WIFI, lpparam, m_pref.getString(SharedPrefs.Wifi.BSSID, "")));
        findAndHookMethod(WifiInfo.class, "getNetworkId", _createReplacementHook(Permissions.WIFI, lpparam, m_pref.getInt(SharedPrefs.Wifi.NETWORK_ID, 0)));
        findAndHookMethod(WifiInfo.class, "getRssi", _createReplacementHook(Permissions.WIFI, lpparam, m_pref.getInt(SharedPrefs.Wifi.RSSI, 0)));
        findAndHookMethod(WifiInfo.class, "getFrequency", _createReplacementHook(Permissions.WIFI, lpparam, m_pref.getInt(SharedPrefs.Wifi.FREQUENCY, 0)));
        findAndHookMethod(WifiInfo.class, "getMacAddress", _createReplacementHook(Permissions.WIFI, lpparam, m_pref.getString(SharedPrefs.Wifi.MAC, "ff:ff:ff:ff:ff:ff")));

        // NFC
        findAndHookMethod(NfcAdapter.class, "getDefaultAdapter", Context.class, _createReplacementHook(Permissions.NFC, lpparam, null));
        findAndHookMethod(NfcAdapter.class, "isEnabled", _createReplacementHook(Permissions.NFC, lpparam, false));


        // Writing to storage
        findAndHookMethod(File.class, "mkdir", _createReplacementHook(Permissions.STORAGE_WRITE, lpparam, false));
        findAndHookMethod(File.class, "mkdirs", _createReplacementHook(Permissions.STORAGE_WRITE, lpparam, false));
        findAndHookMethod(File.class, "canWrite", _createReplacementHook(Permissions.STORAGE_WRITE, lpparam, false));
        findAndHookConstructor(FileOutputStream.class, String.class, m_permissionsHooks.get(Permissions.STORAGE_WRITE));
        findAndHookConstructor(FileOutputStream.class, String.class, boolean.class, m_permissionsHooks.get(Permissions.STORAGE_WRITE));
        findAndHookConstructor(FileOutputStream.class, File.class, m_permissionsHooks.get(Permissions.STORAGE_WRITE));
        findAndHookConstructor(FileOutputStream.class, File.class, boolean.class, m_permissionsHooks.get(Permissions.STORAGE_WRITE));
        findAndHookConstructor(FileOutputStream.class, FileDescriptor.class, _createReplacementHook(Permissions.STORAGE_WRITE, lpparam, null));

        // Reading from storage
        findAndHookConstructor(FileInputStream.class, String.class, m_permissionsHooks.get(Permissions.STORAGE_READ));
        findAndHookConstructor(FileInputStream.class, File.class, m_permissionsHooks.get(Permissions.STORAGE_READ));
        findAndHookMethod(File.class, "listFiles", _createReplacementHook(Permissions.STORAGE_READ, lpparam, new File[]{}));
        findAndHookMethod(File.class, "listFiles", FileFilter.class, _createReplacementHook(Permissions.STORAGE_READ, lpparam, new File[]{}));
        findAndHookMethod(File.class, "listFiles", FilenameFilter.class, _createReplacementHook(Permissions.STORAGE_READ, lpparam, new File[]{}));
        this._hookContentResolverQuery(lpparam, Permissions.STORAGE_READ, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.STORAGE_READ, MediaStore.Video.Media.INTERNAL_CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.STORAGE_READ, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.STORAGE_READ, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.STORAGE_READ, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        this._hookContentResolverQuery(lpparam, Permissions.STORAGE_READ, MediaStore.Audio.Media.INTERNAL_CONTENT_URI);
    }

    private void _hookContentResolverQuery(XC_LoadPackage.LoadPackageParam lpparam, String permission, Uri uriToBlock) {
        XC_MethodHook contentResolverHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                final Uri hookedUri = (Uri) param.args[0];
                if (hookedUri.getAuthority().equals(uriToBlock.getAuthority()) && hookedUri.getPath().equals(uriToBlock.getPath())) {
                    if (!_isRestricted(permission, lpparam)) {
                        return;
                    }
                    if (Permissions.CONTACTS_LIST.equals(permission)) {
                        _contactsBeforeHookedMethod(param, hookedUri, m_pref.getStringSet(SharedPrefs.CONTACTS, new HashSet<>()));
                    } else {
                        param.setResult(Constants.emptyCursor);
                    }
                }
            }
        };
        findAndHookMethod("android.content.ContentResolver", lpparam.classLoader, "query", Uri.class,
                String[].class, String.class, String[].class, String.class, contentResolverHook);
        findAndHookMethod("android.content.ContentResolver", lpparam.classLoader, "query", Uri.class,
                String[].class, Bundle.class, CancellationSignal.class, contentResolverHook);
        findAndHookMethod("android.content.ContentResolver", lpparam.classLoader, "query", Uri.class,
                String[].class, String.class, String[].class, String.class, CancellationSignal.class, contentResolverHook);
    }

    private void _hookSecureStringQuery(XC_LoadPackage.LoadPackageParam lpparam, String permission, String query, String result) {
        XC_MethodHook secureStringHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                final String hookedQuery = (String) param.args[1];
                if (hookedQuery.equals(query)) {
                    if (!_isRestricted(permission, lpparam)) {
                        return;
                    }
                    param.setResult(result);
                }
            }
        };
        findAndHookMethod(Settings.Secure.class, "getString", ContentResolver.class, String.class, secureStringHook);
    }

    private void _contactsBeforeHookedMethod(XC_MethodHook.MethodHookParam param, Uri hookedUri, Set<String> contactIdsSet) {
        if (contactIdsSet.isEmpty()) {
            param.setResult(Constants.emptyCursor);
            return;
        }

        // In case of ContactsContract.Data.CONTENT_URI or ContactsContract.RawContacts.CONTENT_URI contactIdColumnName should be 'contact_id'
        // else (ContactsContract.Contacts.CONTENT_URI), should be '_id'
        final String contactIdColumnName = hookedUri.getPath().equals(ContactsContract.Contacts.CONTENT_URI.getPath()) ?
                ContactsContract.Contacts._ID : ContactsContract.Data.CONTACT_ID;

        // adding contact id projection
        ArrayList<String> projection = new ArrayList<>(Arrays.asList((String[]) param.args[1]));
        if (!projection.contains(contactIdColumnName)) {
            projection.add(contactIdColumnName);
        }

        // params are uri hook(0), projection(1), selection(2), selection args(3)
        // or uri hook(0), projection(1), bundle(2)
        String originalSelection;
        String contactIdsSelection = contactIdColumnName + " IN (" + String.join(",", contactIdsSet) + ")";
        String[] selectionArgs;
        if (param.args[2] instanceof Bundle) {
            Bundle arguments = ((Bundle) param.args[2]);
            originalSelection = arguments.getString(ContentResolver.QUERY_ARG_SQL_SELECTION, "");
            selectionArgs = arguments.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
        } else {
            originalSelection = (String) param.args[2];
            selectionArgs = (String[]) param.args[3];
        }

        if (originalSelection != null) {
            //  if already selecting only the wanted contact ids, returning to the original function
            if (originalSelection.startsWith(contactIdsSelection)) {
                return;
            }
            contactIdsSelection += " AND (" + originalSelection + ")";
        }
        Object[] queryParams = new Object[]{
                hookedUri, projection.toArray(new String[0]),
                contactIdsSelection, selectionArgs, null
        };
        final Method queryMethod = XposedHelpers.findMethodExact(ContentResolver.class, "query", Uri.class,
                String[].class, String.class, String[].class, String.class);
        try {
            param.setResult(XposedBridge.invokeOriginalMethod(
                    queryMethod, AndroidAppHelper.currentApplication().getContentResolver(), queryParams));
        } catch (IllegalAccessException | InvocationTargetException e) {
            param.setResult(Constants.emptyCursor);
        }
    }

    /*
     * function returns a hook used for replacement, it sets the param result
     * to given replacementValue if the permission is restricted
     * */
    private XC_MethodReplacement _createReplacementHook(String permission, XC_LoadPackage.LoadPackageParam lpparam, Object replacementValue) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                if (_isRestricted(permission, lpparam)) {
                    return replacementValue;
                }
                return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
            }
        };
    }

    /* function returns a hook used for app list, it sets the param result
     * to the shared prefs app list
     * */
    private XC_MethodHook _createAppListHook(XC_LoadPackage.LoadPackageParam lpparam, boolean packageInfo) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook.MethodHookParam param) throws Throwable {
                Context context = AndroidAppHelper.currentApplication();
                if (_isRestricted(Permissions.APP_LIST, lpparam)) {
                    ArrayList<Object> apps = new ArrayList<>();
                    for (String packageName : m_pref.getStringSet(SharedPrefs.APP_LIST, new HashSet<>())) {
                        if (packageInfo) {
                            apps.add(context.getPackageManager().getPackageInfo(packageName, 0));
                        } else {
                            apps.add(context.getPackageManager().getApplicationInfo(packageName, 0));
                        }
                    }
                    param.setResult(apps);
                }
            }
        };
    }

    /* function returns a hook used for storage */
    private XC_MethodHook _createStorageHook(XC_LoadPackage.LoadPackageParam lpparam, String permission) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!_isRestricted(permission, lpparam)) {
                    return;
                }
                String path = "";
                if (param.args[0] instanceof File) {
                    param.args[0] = new File(path);
                } else {
                    param.args[0] = path;
                }
            }
        };
    }

    /* function returns a hook used for location, it sets the param result to 0.0 if
     * no sharedPrefs value is set
     * */
    private XC_MethodHook _createLocationHook(XC_LoadPackage.LoadPackageParam lpparam, String sharePrefsKey) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (_isRestricted(Permissions.LOCATION, lpparam)) {
                    double location;
                    try {
                        location = Double.parseDouble(m_pref.getString(sharePrefsKey, "0.0"));
                    } catch (NumberFormatException _e) {
                        location = 0.0;
                    }
                    param.setResult(location);
                }
            }
        };
    }

    /* function checks if the permission is restricted, if so it shows notification and reloads shared prefs */
    private boolean _isRestricted(String permission, XC_LoadPackage.LoadPackageParam lpparam) {
        if (!m_isRestricted.containsKey(permission)) {
            m_isRestricted.put(permission, DatabaseHelper.didUserRestrict(AndroidAppHelper.currentApplication(), permission, lpparam.packageName));
        }
        if (m_isRestricted.get(permission)) {
            _showNotification(permission, lpparam.appInfo);
            m_pref.reload();
            return true;
        }
        return false;
    }

    /* function shows a notification for a used permission */
    private void _showNotification(String Permission, ApplicationInfo appInfo) {
        // Create a notification and set the notification channel.
        Notification notification = new Notification.Builder(m_systemContext, NAME)
                .setContentTitle(NAME)
                .setContentText(Utils.getAppLabel(m_systemContext, appInfo) + " tried to use: " + Permission + ".")
                .setChannelId(NAME)
                .setOnlyAlertOnce(true)
                .build();
        // Issue the notification.
        m_notificationsManager.notify(1, notification);
    }

    private void _hookIdentifications(XC_LoadPackage.LoadPackageParam lpparam) {
        findAndHookMethod(TelephonyManager.class, "getDeviceId", m_permissionsHooks.get(SharedPrefs.Identifications.IMEI));
        findAndHookMethod(TelephonyManager.class, "getDeviceId", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.IMEI));
        findAndHookMethod(TelephonyManager.class, "getImei", m_permissionsHooks.get(SharedPrefs.Identifications.IMEI));
        findAndHookMethod(TelephonyManager.class, "getImei", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.IMEI));
        this._hookSecureStringQuery(lpparam, Permissions.IDENTIFICATIONS, Settings.Secure.ANDROID_ID, m_pref.getString(SharedPrefs.Identifications.ANDROID_ID, ""));
        findAndHookMethod(TelephonyManager.class, "getLine1Number", _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, m_pref.getString(SharedPrefs.Identifications.PHONE_NUMBER, "")));
        findAndHookMethod(TelecomManager.class, "getLine1Number", PhoneAccountHandle.class, _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, m_pref.getString(SharedPrefs.Identifications.PHONE_NUMBER, "")));
        findAndHookMethod(SubscriptionInfo.class, "getNumber", _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, m_pref.getString(SharedPrefs.Identifications.PHONE_NUMBER, "")));
        findAndHookMethod(TelephonyManager.class, "getMeid", m_permissionsHooks.get(SharedPrefs.Identifications.MEID));
        findAndHookMethod(TelephonyManager.class, "getMeid", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.MEID));
        findAndHookMethod(CellIdentityGsm.class, "getMobileNetworkOperator", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getNetworkOperator", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getNetworkOperator", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getNetworkOperatorName", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getNetworkOperatorForPhone", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getNetworkOperatorName", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(SubscriptionInfo.class, "getDisplayName", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(SubscriptionInfo.class, "getCarrierName", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getSimOperator", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getSimOperator", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getSimCarrierIdName", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getSimOperatorName", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getSimOperatorName", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getSimOperatorNameForPhone", int.class, m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(TelephonyManager.class, "getAllCellInfo", _createReplacementHook(Permissions.IDENTIFICATIONS, lpparam, null));
        findAndHookMethod(CellIdentity.class, "getOperatorAlphaLong", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(ServiceState.class, "getOperatorAlphaLong", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(ServiceState.class, "getVoiceOperatorAlphaShort", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(ServiceState.class, "getDataOperatorAlphaShort", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(ServiceState.class, "getOperatorAlphaLongRaw", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(CellIdentity.class, "getOperatorAlphaShort", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
        findAndHookMethod(ServiceState.class, "getOperatorAlphaShort", m_permissionsHooks.get(SharedPrefs.Identifications.OPERATOR));
    }
}