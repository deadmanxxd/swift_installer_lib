/*
 *
 *  * Copyright (C) 2018 Griffin Millender
 *  * Copyright (C) 2018 Per Lycke
 *  * Copyright (C) 2018 Davide Lilli & Nishith Khanna
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.brit.swiftinstaller.library.ui.activities

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.brit.swiftinstaller.library.R
import com.brit.swiftinstaller.library.installer.rom.RomInfo
import com.brit.swiftinstaller.library.ui.applist.AppItem
import com.brit.swiftinstaller.library.ui.applist.AppListFragment
import com.brit.swiftinstaller.library.ui.applist.AppsTabPagerAdapter
import com.brit.swiftinstaller.library.utils.*
import com.brit.swiftinstaller.library.utils.OverlayUtils.isOverlayEnabled
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_install_summary.*
import kotlinx.android.synthetic.main.tab_layout_install_summary.*
import java.io.File
import java.lang.ref.WeakReference

class InstallSummaryActivity : ThemeActivity() {

    companion object {
        private const val SUCCESS_TAB = 0
        const val FAILED_TAB = 1

        const val ACTION_INSTALL_CANCELLED = "com.brit.swiftinstaller.action.INSTALL_CANCELLED"
    }

    private lateinit var mPagerAdapter: AppsTabPagerAdapter
    private val mHandler = Handler()

    private var mErrorMap: HashMap<String, String> = HashMap()
    private var mApps = arrayListOf<String>()

    var update = false

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install_summary)
        File(Environment.getExternalStorageDirectory(), ".swift").deleteRecursively()

        update = intent.getBooleanExtra("update", false)

        mPagerAdapter = AppsTabPagerAdapter(supportFragmentManager, true, SUCCESS_TAB, FAILED_TAB)
        mPagerAdapter.setAlertIconClickListener(object : AppListFragment.AlertIconClickListener {
            override fun onAlertIconClick(appItem: AppItem) {
                val dialog = AlertDialog.Builder(this@InstallSummaryActivity, R.style.AppTheme_AlertDialog_Error)
                        .setTitle(appItem.title)
                        .setIcon(appItem.icon)
                        .setMessage(mErrorMap[appItem.packageName])
                        .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int ->
                            dialogInterface.dismiss()
                        }
                themeDialog()
                dialog.show()
            }

        })

        container.adapter = mPagerAdapter
        container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tab_install_summary_root))
        tab_install_summary_root.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(container))

        //updateList()
    }

    @Suppress("UNUSED_PARAMETER")
    fun fabFinishedClick(view: View) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = View.inflate(this, R.layout.sheet_reboot, null)
        bottomSheetDialog.setContentView(sheetView)
        sheetView.setBackgroundColor(getBackgroundColor(this))
        bottomSheetDialog.show()

        val sendLog = sheetView.findViewById<View>(R.id.send_email_layout)
        val reboot = sheetView.findViewById<View>(R.id.reboot_layout)

        if (mErrorMap.isNotEmpty()) {
            sendLog.visibility = View.VISIBLE
            sendLog.setOnClickListener {
                sendErrorLog()
            }
        }

        if (mPagerAdapter.getAppsCount(0) > 0) {
            reboot.visibility = View.VISIBLE
            reboot.setOnClickListener {
                bottomSheetDialog.dismiss()
                reboot()
            }
        }
    }

    private fun reboot() {
        val rebootDialog = Dialog(this, R.style.AppTheme_Translucent)
        rebootDialog.setContentView(R.layout.reboot)
        rebootDialog.show()
        mHandler.post {
            rebootCommand()
        }
    }

    private fun updateList() {
        mApps.clear()
        mApps.addAll(swift.installApps)
        mErrorMap.clear()
        mErrorMap.putAll(swift.errorMap)
        mPagerAdapter.clearApps()
        AppLoader(this, mApps, mErrorMap, update, object : OverlaysActivity.Callback {
            override fun updateApps(tab: Int, item: AppItem) {
                mPagerAdapter.addApp(tab, item)
            }
        }).execute()

        if (!ShellUtils.isRootAvailable && mErrorMap.isNotEmpty()) {
            send_email_layout.visibility = View.VISIBLE
            send_email_btn.setOnClickListener {
                sendErrorLog()
            }
        }

        val hotSwap = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("hotswap", false)

        if (ShellUtils.isRootAvailable && !hotSwap) {
            fab_install_finished.show()
        }
        if (!hotSwap || !isOverlayEnabled("android")) {
            mHandler.post {
                resultDialog()
            }
        } else {
            restartSysUi()
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("hotswap", false).apply()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateList()
    }

    private fun resultDialog() {
        val builder = AlertDialog.Builder(this)
        val failed = mApps.size == mErrorMap.size

        builder.setTitle(if (failed) {
            R.string.installation_failed
        } else if (!ShellUtils.isRootAvailable) {
            R.string.reboot_to_finish
        } else {
            R.string.reboot_now_title
        })

        builder.setMessage(when {
            failed -> R.string.examined_result_msg_error
            mErrorMap.isNotEmpty() -> R.string.examined_result_msg
            else -> R.string.examined_result_msg_noerror
        })

        if (ShellUtils.isRootAvailable && !failed) {
            builder.setNegativeButton(R.string.reboot_later) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            builder.setPositiveButton(R.string.reboot_now) { dialogInterface, _ ->
                dialogInterface.dismiss()
                reboot()
            }
        } else {
            builder.setPositiveButton(R.string.got_it) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
        }

        themeDialog()
        dialog = builder.create()
        dialog?.show()

        if (!failed) {
            container.currentItem = 0
        } else {
            container.currentItem = 1
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun sendErrorLog() {
        val emailIntent = Intent(Intent.ACTION_SENDTO)
        emailIntent.data = Uri.parse("mailto:")
        emailIntent.putExtra(Intent.EXTRA_EMAIL, Array(1) { "swiftuserhelp@gmail.com" })
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Swift Installer: Error Log")

        val text = StringBuilder()
        text.append("\n")
        text.append("Installer Version: ${getString(R.string.lib_version)}")
        text.append("\n")
        text.append("Device: ${Build.DEVICE}")
        text.append("\n")
        text.append("Android Version: ${Build.VERSION.RELEASE}")
        text.append("\n")
        text.append("**********************************")
        text.append("\n")
        for (item in mPagerAdapter.getApps(FAILED_TAB).iterator()) {
            if (mErrorMap.containsKey(item.packageName)) {
                text.append("App: " + item.title)
                text.append("\n")
                text.append("App Package: " + item.packageName)
                text.append("\n")
                text.append("App Version: " + item.versionName)
                text.append("\n")
                text.append("Error Log: " + mErrorMap[item.packageName])
                text.append("\n")
                text.append("-------------------")
                text.append("\n")
            }
        }

        emailIntent.putExtra(Intent.EXTRA_TEXT, text.toString())
        startActivity(emailIntent)
    }

    class AppLoader(context: Context, val apps: ArrayList<String>,
                    private val errorMap: HashMap<String, String>,
                    private val update: Boolean,
                    private val mCallback: OverlaysActivity.Callback) :
            AsyncTask<Void, AppLoader.Progress, Void>() {

        private val mConRef: WeakReference<Context> = WeakReference(context)
        private val mHandler = Handler()

        class Progress(val tab: Int, val item: AppItem)

        override fun doInBackground(vararg params: Void?): Void? {
            assert(mConRef.get() != null)
            val pm = mConRef.get()!!.packageManager
            val context = mConRef.get()

            apps.addAll(errorMap.keys)
            for (pn: String in apps) {
                if (context == null) continue
                var info: ApplicationInfo? = null
                var pInfo: PackageInfo? = null
                var oInfo: PackageInfo? = null
                try {
                    info = pm.getApplicationInfo(pn, PackageManager.GET_META_DATA)
                    pInfo = pm.getPackageInfo(pn, 0)
                    oInfo = RomInfo.getRomInfo(context).getOverlayInfo(pm, pn)
                } catch (ex: Exception) {
                }
                if (info != null) {
                    val item = AppItem()
                    item.packageName = pn
                    item.icon = pm.getApplicationIcon(item.packageName)
                    item.title = info.loadLabel(pm) as String
                    item.versionCode = pInfo!!.getVersionCode()
                    item.versionName = pInfo.versionName
                    if (errorMap.keys.contains(pn)) {
                        onProgressUpdate(Progress(FAILED_TAB, item))
                    } else if (RomInfo.getRomInfo(context).isOverlayInstalled(pn)) {
                        if (update && oInfo!!.getVersionCode() != OverlayUtils.getOverlayVersion(context, item.packageName)) {
                            errorMap[pn] = "Update Failed"
                            onProgressUpdate(Progress(FAILED_TAB, item))
                        } else {
                            onProgressUpdate(Progress(SUCCESS_TAB, item))
                            removeAppToUpdate(context, item.packageName)
                        }
                    } else {
                        errorMap[pn] = "Install Cancelled"
                        LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(Intent(ACTION_INSTALL_CANCELLED))
                        onProgressUpdate(Progress(FAILED_TAB, item))
                    }
                }
            }
            return null
        }

        override fun onProgressUpdate(vararg progress: Progress?) {
            super.onProgressUpdate(*progress)
            mHandler.post {
                mCallback.updateApps(progress[0]!!.tab, progress[0]!!.item)
            }
        }

    }
}
