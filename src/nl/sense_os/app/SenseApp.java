/*
 * *************************************************************************************************
 * Copyright (C) 2010 Sense Observation Systems, Rotterdam, the Netherlands. All rights reserved.
 * *************************************************************************************************
 */

package nl.sense_os.app;

import nl.sense_os.app.login.LoginActivity;
import nl.sense_os.app.register.RegisterActivity;
import nl.sense_os.service.ISenseService;
import nl.sense_os.service.ISenseServiceCallback;
import nl.sense_os.service.SenseService;
import nl.sense_os.service.constants.SensePrefs;
import nl.sense_os.service.constants.SensePrefs.Auth;
import nl.sense_os.service.constants.SenseStatusCodes;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class SenseApp extends Activity {

    private class Dialogs {
        static final int FAQ = 1;
        static final int HELP = 2;
        static final int UPDATE_ALERT = 3;
        public static final int LOGOUT = 4;
    };

    /**
     * Service stub for callbacks from the Sense service.
     */
    private class SenseCallback extends ISenseServiceCallback.Stub {

        @Override
        public void onChangeLoginResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void onRegisterResult(int result) throws RemoteException {
            // not used
        }

        @Override
        public void statusReport(final int status) {
            // Log.v(TAG, "Received status report from Sense Platform service...");

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    updateUi(status);
                }
            });
        }
    }

    /**
     * Service connection to handle connection with the Sense service. Manages the
     * <code>service</code> field when the service is connected or disconnected.
     */
    private class SenseServiceConn implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            // Log.v(TAG, "Bound to Sense Platform service...");

            service = ISenseService.Stub.asInterface(binder);
            try {
                service.getStatus(callback);
                long lastLogin = service.getPrefLong(SensePrefs.Main.LAST_LOGGED_IN, -1);
                if (lastLogin == -1) {
                    // sense has never been logged in
                    showDialog(Dialogs.HELP);
                } else {
                    CharSequence loginDate = DateFormat.format("dd/MM/yyyy kk:mm", lastLogin);
                    Log.v(TAG, "Last succesful login: " + loginDate);
                }
            } catch (final RemoteException e) {
                Log.e(TAG, "Error checking service status after binding. ", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // Log.v(TAG, "Sense Platform service disconnected...");

            /* this is not called when the service is stopped, only when it is suddenly killed! */
            service = null;
            isServiceBound = false;
            checkServiceStatus();
        }
    }

    /**
     * Receiver for broadcast events from the Sense Service, e.g. when the status of the service
     * changes.
     */
    private class SenseServiceListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    checkServiceStatus();
                }
            });
        }
    }

    private static final String TAG = "SenseApp";

    private final ISenseServiceCallback callback = new SenseCallback();
    private boolean isServiceBound;
    private ISenseService service;
    private final ServiceConnection serviceConn = new SenseServiceConn();
    private final SenseServiceListener serviceListener = new SenseServiceListener();

    /**
     * Binds to the Sense Service, creating it if necessary.
     */
    private void bindToSenseService() {

        // start the service if it was not running already
        if (!isServiceBound) {
            // Log.v(TAG, "Try to bind to Sense Platform service");
            final Intent serviceIntent = new Intent(getString(R.string.action_sense_service));
            isServiceBound = bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
        } else {
            // already bound
        }
    }

    /**
     * Calls {@link ISenseService#getStatus(ISenseServiceCallback)} on the service. This will
     * generate a callback that updates the buttons ToggleButtons showing the service's state.
     */
    private void checkServiceStatus() {
        // Log.v(TAG, "Checking service status..");

        if (null != service) {
            try {
                // request status report
                service.getStatus(callback);
            } catch (final RemoteException e) {
                Log.e(TAG, "Error checking service status. ", e);
            }
        } else {
            // Log.v(TAG, "Not bound to Sense Platform service! Assume it's not running...");

            // invoke callback method directly to update UI anyway.
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    updateUi(0);
                }
            });
        }
    }

    @TargetApi(11)
    private Dialog createDialogFaq() {
        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // specifically set dark theme for Android 3.0+
            builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        }

        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(R.string.dialog_faq_title);
        builder.setMessage(R.string.dialog_faq_msg);
        builder.setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    /**
     * @return a help dialog, which explains the goal of Sense and clicks through to Registration or
     *         Login.
     */
    @TargetApi(11)
    private Dialog createDialogHelp() {
        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // specifically set dark theme for Android 3.0+
            builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        }

        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(R.string.dialog_welcome_title);
        builder.setMessage(R.string.dialog_welcome_msg);
        builder.setPositiveButton(R.string.button_login, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                startLogin();
            }
        });
        builder.setNeutralButton(R.string.button_reg, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                startRegister();
            }
        });
        builder.setNegativeButton(R.string.button_faq, new OnClickListener() {

            @SuppressWarnings("deprecation")
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showDialog(Dialogs.FAQ);
            }
        });
        return builder.create();
    }

    /**
     * @return a dialog to alert the user for changes in the CommonSense.
     */
    @TargetApi(11)
    private Dialog createDialogUpdateAlert() {
        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // specifically set dark theme for Android 3.0+
            builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        }

        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.dialog_update_msg);
        builder.setTitle(R.string.dialog_update_title);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setCancelable(false);
        return builder.create();
    }

    /**
     * @return a dialog to confirm if the user want to log out.
     */
    @TargetApi(11)
    private Dialog createDialogLogout() {

        // create builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // specifically set dark theme for Android 3.0+
            builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        }

        // get username
        String username = null;
        if (null != service) {
            try {
                username = service.getPrefString(Auth.LOGIN_USERNAME, null);
            } catch (RemoteException e) {
                // should never happen
            }
        }

        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.dialog_logout_msg);
        builder.setTitle(getString(R.string.dialog_logout_title, username));
        builder.setPositiveButton(R.string.button_logout, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                logout();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    /**
     * Handles clicks on the UI.
     * 
     * @param v
     *            the View that was clicked.
     */
    public void onClick(View v) {

        boolean oldState = false;
        if (v.getId() == R.id.main_field) {
            final CheckBox cb = (CheckBox) findViewById(R.id.main_cb);
            oldState = cb.isChecked();
            // cb.setChecked(!oldState);
            toggleMain(!oldState);
        } else if (v.getId() == R.id.device_prox_field) {
            final CheckBox devProx = (CheckBox) findViewById(R.id.device_prox_cb);
            if (devProx.isEnabled()) {
                oldState = devProx.isChecked();
                // devProx.setChecked(!oldState);
                toggleDeviceProx(!oldState);
            }
        } else if (v.getId() == R.id.location_field) {
            final CheckBox location = (CheckBox) findViewById(R.id.location_cb);
            if (location.isEnabled()) {
                oldState = location.isChecked();
                // location.setChecked(!oldState);
                toggleLocation(!oldState);
            }
        } else if (v.getId() == R.id.motion_field) {
            final CheckBox motion = (CheckBox) findViewById(R.id.motion_cb);
            if (motion.isEnabled()) {
                oldState = motion.isChecked();
                // motion.setChecked(!oldState);
                toggleMotion(!oldState);
            }
        } else if (v.getId() == R.id.external_sensor_field) {
            final CheckBox external = (CheckBox) findViewById(R.id.external_sensor_cb);
            if (external.isEnabled()) {
                oldState = external.isChecked();
                // external.setChecked(!oldState);
                toggleExternalSensors(!oldState);
            }
        } else if (v.getId() == R.id.ambience_field) {
            final CheckBox ambience = (CheckBox) findViewById(R.id.ambience_cb);
            if (ambience.isEnabled()) {
                oldState = ambience.isChecked();
                // ambience.setChecked(!oldState);
                toggleAmbience(!oldState);
            }
        } else if (v.getId() == R.id.phonestate_field) {
            final CheckBox phoneState = (CheckBox) findViewById(R.id.phonestate_cb);
            if (phoneState.isEnabled()) {
                oldState = phoneState.isChecked();
                // phoneState.setChecked(!oldState);
                togglePhoneState(!oldState);
            }
        } else if (v.getId() == R.id.popquiz_field) {
            final CheckBox quiz = (CheckBox) findViewById(R.id.popquiz_cb);
            if (quiz.isEnabled()) {
                oldState = quiz.isChecked();
                // quiz.setChecked(!oldState);
                togglePopQuiz(!oldState);
            }
        } else if (v.getId() == R.id.prefs_field) {
            startActivity(new Intent(getString(R.string.action_sense_settings)));
        } else {
            Log.e(TAG, "Unknown button pressed!");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        switch (id) {
        case Dialogs.FAQ:
            dialog = createDialogFaq();
            break;
        case Dialogs.UPDATE_ALERT:
            dialog = createDialogUpdateAlert();
            break;
        case Dialogs.HELP:
            dialog = createDialogHelp();
            break;
        case Dialogs.LOGOUT:
            dialog = createDialogLogout();
            break;
        default:
            Log.w(TAG, "Trying to create unexpected dialog, ignoring input...");
            break;
        }
        return dialog;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        // stop the service if it is not running anymore
        if (false == ((CheckBox) findViewById(R.id.main_cb)).isChecked()) {
            stopService(new Intent(getString(R.string.action_sense_service)));
        }
        unbindFromSenseService();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_faq:
            showDialog(Dialogs.FAQ);
            break;
        case R.id.menu_preferences:
            startActivity(new Intent(getString(R.string.action_sense_settings)));
            break;
        case R.id.menu_login:
            startLogin();
            break;
        case R.id.menu_logout:
            showDialog(Dialogs.LOGOUT);
            break;
        case R.id.menu_register:
            startRegister();
            break;
        default:
            Log.w(TAG, "Unexpected option item selected: " + item);
            return false;
        }
        return true;
    }

    private void logout() {
        try {
            service.logout();
            service.toggleMain(false);
            service.getStatus(callback);
        } catch (RemoteException e) {
            Log.e(TAG, "failed to log out: " + e);
        }
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == Dialogs.LOGOUT) {
            String username = null;
            if (null != service) {
                try {
                    username = service.getPrefString(Auth.LOGIN_USERNAME, null);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get USERNAME pref: " + e);
                }
                dialog.setTitle(getString(R.string.dialog_logout_title, username));
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        boolean loggedIn = false;
        if (null != service) {
            try {
                loggedIn = service.getPrefString(Auth.LOGIN_USERNAME, null) != null;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to get USERNAME preference from service: " + e);
            }
        }

        menu.findItem(R.id.menu_login).setVisible(!loggedIn);
        menu.findItem(R.id.menu_logout).setVisible(loggedIn);
        menu.findItem(R.id.menu_register).setVisible(!loggedIn);

        return true;
    }

    @Override
    protected void onStart() {
        // Log.v(TAG, "onStart");
        super.onStart();

        // bind to service as soon as possible
        bindToSenseService();

        // register receiver for updates
        IntentFilter filter = new IntentFilter(SenseService.ACTION_SERVICE_BROADCAST);
        registerReceiver(serviceListener, filter);

        checkServiceStatus();
    }

    @Override
    protected void onStop() {
        // Log.v(TAG, "onStop");

        // unregister service state listener
        try {
            unregisterReceiver(serviceListener);
        } catch (IllegalArgumentException e) {
            // listener was not registered
        }

        super.onStop();
    }

    private void showToast(final CharSequence text, final int duration) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(SenseApp.this, text, duration).show();
            }
        });
    }

    private void startLogin() {
        startActivity(new Intent(this, LoginActivity.class));
    }

    private void startRegister() {
        startActivity(new Intent(this, RegisterActivity.class));
    }

    private void toggleAmbience(boolean active) {
        // Log.v(TAG, "Toggle ambience: " + active);

        if (null != service) {

            try {
                service.toggleAmbience(active);

                // show informational toast
                if (active) {

                    final int rate = Integer.parseInt(service.getPrefString(
                            SensePrefs.Main.SAMPLE_RATE, "0"));
                    String intervalString = "";
                    String extraString = "";
                    switch (rate) {
                    case -2:
                        intervalString = "the whole time";
                        extraString = " A sound stream will be uploaded.";
                        break;
                    case -1:
                        // often
                        intervalString = "every 10 seconds";
                        break;
                    case 0:
                        // normal
                        intervalString = "every minute";
                        break;
                    case 1:
                        // rarely (1 hour)
                        intervalString = "every 15 minutes";
                        break;
                    default:
                        Log.e(TAG, "Unexpected commonsense rate preference.");
                    }
                    String msg = getString(R.string.toast_toggle_ambience).replace("?",
                            intervalString)
                            + extraString;
                    showToast(msg, Toast.LENGTH_LONG);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException toggling ambience service.");
            }

        } else {
            Log.w(TAG, "Could not toggle ambience service: Sense service is not bound.");
        }

        checkServiceStatus();
    }

    private void toggleDeviceProx(boolean active) {
        // Log.v(TAG, "Toggle neighboring devices: " + active);

        if (null != service) {
            try {
                service.toggleDeviceProx(active);

                // show informational Toast
                if (active) {

                    final int rate = Integer.parseInt(service.getPrefString(
                            SensePrefs.Main.SAMPLE_RATE, "0"));
                    String interval = "";
                    switch (rate) {
                    case -2: // real-time
                        interval = "second";
                        break;
                    case -1: // often
                        interval = "minute";
                        break;
                    case 0: // normal
                        interval = "5 minutes";
                        break;
                    case 1: // rarely (15 hour)
                        interval = "15 minutes";
                        break;
                    default:
                        Log.e(TAG, "Unexpected device prox preference.");
                    }
                    final String msg = getString(R.string.toast_toggle_dev_prox);
                    showToast(msg.replace("?", interval), Toast.LENGTH_LONG);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException toggling device proximity service.");
            }

        } else {
            Log.w(TAG, "Could not toggle device proximity service: Sense service is not bound.");
        }

        checkServiceStatus();
    }

    private void toggleExternalSensors(boolean active) {
        // Log.v(TAG, "Toggle external sensors: " + active);

        if (null != service) {

            try {
                service.toggleExternalSensors(active);

                // show informational toast
                if (active) {

                    final int rate = Integer.parseInt(service.getPrefString(
                            SensePrefs.Main.SAMPLE_RATE, "0"));
                    String interval = "";
                    switch (rate) {
                    case -2: // often
                        interval = "second";
                        break;
                    case -1: // often
                        interval = "5 seconds";
                        break;
                    case 0: // normal
                        interval = "minute";
                        break;
                    case 1: // rarely
                        interval = "15 minutes";
                        break;
                    default:
                        Log.e(TAG, "Unexpected commonsense rate: " + rate);
                        break;
                    }
                    final String msg = getString(R.string.toast_toggle_external_sensors).replace(
                            "?", interval);
                    showToast(msg, Toast.LENGTH_LONG);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException toggling external sensors service.");
            }

        } else {
            Log.w(TAG, "Could not toggle external sensors service: Sense service is not bound.");
        }

        checkServiceStatus();
    }

    private void toggleLocation(boolean active) {
        // Log.v(TAG, "Toggle location: " + active);

        if (null != service) {

            try {
                service.toggleLocation(active);

                // show informational toast
                if (active) {

                    final int rate = Integer.parseInt(service.getPrefString(
                            SensePrefs.Main.SAMPLE_RATE, "0"));
                    String interval = "";
                    switch (rate) {
                    case -2: // often
                        interval = "second";
                        break;
                    case -1: // often
                        interval = "30 seconds";
                        break;
                    case 0: // normal
                        interval = "5 minutes";
                        break;
                    case 1: // rarely
                        interval = "15 minutes";
                        break;
                    default:
                        Log.e(TAG, "Unexpected commonsense rate: " + rate);
                        break;
                    }
                    final String msg = getString(R.string.toast_toggle_location).replace("?",
                            interval);
                    showToast(msg, Toast.LENGTH_LONG);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException toggling location service.");
            }
        } else {
            Log.w(TAG, "Could not toggle location service: Sense service is not bound.");
        }

        checkServiceStatus();
    }

    /**
     * Toggles the Sense service state. The service is started using <code>startService</code>, and
     * then the activity binds to the service. Alternatively, the service is stopped and the
     * Activity unbinds itself.
     * 
     * Afterwards, the UI is updated to make the ToggleButtons show the new service state.
     */
    private void toggleMain(boolean active) {
        // Log.v(TAG, "Toggle main: " + active);

        if (null != service) {
            try {
                if (active && null == service.getPrefString(Auth.LOGIN_USERNAME, null)) {
                    // cannot activate the service: Sense does not know the username yet
                    Log.w(TAG, "Cannot start Sense Platform without username");
                    startLogin();
                } else {
                    // normal situation
                    service.toggleMain(active);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception toggling Sense Platform service main status!");
            }
        }

        checkServiceStatus();
    }

    private void toggleMotion(boolean active) {
        // Log.v(TAG, "Toggle motion: " + active);

        if (null != service) {

            try {
                service.toggleMotion(active);

                // show informational toast
                if (active) {

                    final int rate = Integer.parseInt(service.getPrefString(
                            SensePrefs.Main.SAMPLE_RATE, "0"));
                    String interval = "";
                    switch (rate) {
                    case -2: // often
                        interval = "second";
                        break;
                    case -1: // often
                        interval = "5 seconds";
                        break;
                    case 0: // normal
                        interval = "minute";
                        break;
                    case 1: // rarely
                        interval = "15 minutes";
                        break;
                    default:
                        Log.e(TAG, "Unexpected commonsense rate: " + rate);
                        break;
                    }
                    final String msg = getString(R.string.toast_toggle_motion).replace("?",
                            interval);
                    showToast(msg, Toast.LENGTH_LONG);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException toggling motion service.");
            }

        } else {
            Log.w(TAG, "Could not toggle motion service: Sense service is not bound.");
        }

        checkServiceStatus();
    }

    private void togglePhoneState(boolean active) {
        // Log.v(TAG, "Toggle phone state: " + active);

        // toggle state in service
        if (null != service) {

            try {
                service.togglePhoneState(active);

                // show informational toast
                if (active) {
                    final String msg = getString(R.string.toast_toggle_phonestate);
                    showToast(msg, Toast.LENGTH_LONG);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException toggling phone state service.");
            }

        } else {
            Log.w(TAG, "Could not toggle phone state service: Sense service is not bound.");
        }

        checkServiceStatus();
    }

    private void togglePopQuiz(boolean active) {

        Log.w(TAG, "Toggle Questionnare not implemented");

        // final SharedPreferences statusPrefs = getSharedPreferences(SensePrefs.STATUS_PREFS,
        // MODE_WORLD_WRITEABLE);
        // final Editor editor = statusPrefs.edit();
        // editor.putBoolean(SensePrefs.Keys.PREF_STATUS_POPQUIZ, active).commit();
        //
        // if (null != this.service) {
        // try {
        // this.service.togglePopQuiz(active, callback);
        //
        // // show informational toast
        // if (active) {
        //
        // final SharedPreferences mainPrefs = getSharedPreferences(SensePrefs.MAIN_PREFS,
        // MODE_WORLD_WRITEABLE);
        // final int rate = Integer.parseInt(mainPrefs.getString(SensePrefs.Keys.PREF_QUIZ_RATE,
        // "0"));
        // String interval = "ERROR";
        // switch (rate) {
        // case -1 : // often (5 mins)
        // interval = "5 minutes";
        // break;
        // case 0 : // normal (15 mins)
        // interval = "15 minutes";
        // break;
        // case 1 : // rarely (1 hour)
        // interval = "hour";
        // break;
        // default :
        // Log.e(TAG, "Unexpected quiz rate preference: " + rate);
        // break;
        // }
        //
        // String msg = getString(R.string.toast_toggle_quiz).replace("?", interval);
        // Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        // }
        //
        // } catch (RemoteException e) {
        // Log.e(TAG, "RemoteException toggling periodic popup service.");
        // }
        // } else {
        // Log.w(TAG, "Could not toggle periodic popup service: Sense service is not bound.");
        // }

        checkServiceStatus();
    }

    /**
     * Unbinds from the Sense service, resets {@link #service} and {@link #isServiceBound}.
     */
    private void unbindFromSenseService() {

        if (true == isServiceBound && null != serviceConn) {
            // Log.v(TAG, "Unbind from Sense Platform service");
            unbindService(serviceConn);
        } else {
            // already unbound
        }
        service = null;
        isServiceBound = false;
    }

    /**
     * Enables the checkboxes to show the status of the Sense Platform service.
     * 
     * @param status
     *            The status of the service.
     * @see {@link Constants#STATUSCODE_RUNNING}
     */
    private void updateUi(int status) {

        final boolean running = (status & SenseStatusCodes.RUNNING) > 0;
        // Log.v(TAG, "'running' status: " + running);
        ((CheckBox) findViewById(R.id.main_cb)).setChecked(running);

        final boolean connected = (status & SenseStatusCodes.CONNECTED) > 0;
        // Log.v(TAG, "'connected' status: " + connected);

        // show connection status in main service field
        TextView mainFirstLine = (TextView) findViewById(R.id.main_firstline);
        if (connected) {
            mainFirstLine.setText("Sense service");
        } else {
            mainFirstLine.setText("Sense service (not logged in)");
        }

        // change description of main service field
        CheckBox mainButton = (CheckBox) findViewById(R.id.main_cb);
        mainButton.setChecked(running);
        TextView mainDescription = (TextView) findViewById(R.id.main_secondLine);
        if (running) {
            mainDescription.setText("Press to disable sensing");
        } else {
            mainDescription.setText("Press to enable sensing");
        }

        // enable phone state list row
        CheckBox button = (CheckBox) findViewById(R.id.phonestate_cb);
        final boolean callstate = (status & SenseStatusCodes.PHONESTATE) > 0;
        button.setChecked(callstate);
        button.setEnabled(running);
        View text1 = findViewById(R.id.phonestate_firstline);
        View text2 = findViewById(R.id.phonestate_secondLine);
        text1.setEnabled(running);
        text2.setEnabled(running);
        if (callstate) {
            // Log.v(TAG, "'phone state' enabled");
        }

        // enable location list row
        button = (CheckBox) findViewById(R.id.location_cb);
        final boolean location = (status & SenseStatusCodes.LOCATION) > 0;
        button.setChecked(location);
        button.setEnabled(running);
        button = (CheckBox) findViewById(R.id.ambience_cb);
        text1 = findViewById(R.id.location_firstline);
        text2 = findViewById(R.id.location_secondLine);
        text1.setEnabled(running);
        text2.setEnabled(running);
        if (location) {
            // Log.v(TAG, "'location' enabled");
        }

        // enable motion list row
        button = (CheckBox) findViewById(R.id.motion_cb);
        final boolean motion = (status & SenseStatusCodes.MOTION) > 0;
        button.setChecked(motion);
        button.setEnabled(running);
        text1 = findViewById(R.id.motion_firstline);
        text2 = findViewById(R.id.motion_secondLine);
        text1.setEnabled(running);
        text2.setEnabled(running);
        if (motion) {
            // Log.v(TAG, "'motion' enabled");
        }

        // enable ambience list row
        button = (CheckBox) findViewById(R.id.ambience_cb);
        final boolean ambience = (status & SenseStatusCodes.AMBIENCE) > 0;
        button.setChecked(ambience);
        button.setEnabled(running);
        button = (CheckBox) findViewById(R.id.ambience_cb);
        text1 = findViewById(R.id.ambience_firstline);
        text2 = findViewById(R.id.ambience_secondLine);
        text1.setEnabled(running);
        text2.setEnabled(running);
        if (ambience) {
            // Log.v(TAG, "'ambience' enabled");
        }

        // enable device proximity row
        button = (CheckBox) findViewById(R.id.device_prox_cb);
        final boolean deviceProx = (status & SenseStatusCodes.DEVICE_PROX) > 0;
        button.setChecked(deviceProx);
        button.setEnabled(running);
        text1 = findViewById(R.id.device_prox_firstline);
        text2 = findViewById(R.id.device_prox_secondLine);
        text1.setEnabled(running);
        text2.setEnabled(running);
        if (deviceProx) {
            // Log.v(TAG, "'neighboring devices' enabled");
        }

        // enable external sensor list row
        button = (CheckBox) findViewById(R.id.external_sensor_cb);
        final boolean external_sensors = (status & SenseStatusCodes.EXTERNAL) > 0;
        button.setChecked(external_sensors);
        button.setEnabled(running);
        text1 = findViewById(R.id.external_sensor_firstline);
        text2 = findViewById(R.id.external_sensor_secondLine);
        text1.setEnabled(running);
        text2.setEnabled(running);
        if (external_sensors) {
            // Log.v(TAG, "'external sensors' enabled");
        }

        // enable pop quiz list row
        button = (CheckBox) findViewById(R.id.popquiz_cb);
        final boolean popQuiz = (status & SenseStatusCodes.QUIZ) > 0;
        button.setChecked(popQuiz);
        button.setEnabled(false);
        text1 = findViewById(R.id.popquiz_firstline);
        text2 = findViewById(R.id.popquiz_secondLine);
        text1.setEnabled(false);
        text2.setEnabled(false);
        if (popQuiz) {
            // Log.v(TAG, "'questionnaire' enabled");
        }
    }
}
