/*
 *
 *  * Copyright (C) 2019 Griffin Millender
 *  * Copyright (C) 2019 Per Lycke
 *  * Copyright (C) 2019 Davide Lilli & Nishith Khanna
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

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.*
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.brit.swiftinstaller.library.R
import com.brit.swiftinstaller.library.installer.Notifier
import com.brit.swiftinstaller.library.utils.*
import kotlinx.android.synthetic.main.progress_dialog_install.view.*
import kotlin.collections.set

class InstallActivity : ThemeActivity() {

    private lateinit var appIcon: ImageView
    private lateinit var appTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressCount: TextView
    private lateinit var progressPercent: TextView
    private val installListener = InstallListener()
    private val handler = Handler()

    private var uninstall = false
    private var update = false

    private var dialog: AlertDialog? = null
    private val dialogState = Bundle()

    private lateinit var apps: SynchronizedArrayList<String>
    private val updateAppsToUninstall = SynchronizedArrayList<String>()

    private val errorMap: HashMap<String, String> = HashMap()

    @Suppress("UNUSED_PARAMETER")
    fun updateProgress(label: String?, icon: Drawable?, prog: Int, max: Int, uninstall: Boolean) {
        var progress = prog
        if (!Utils.isSamsungOreo()) {
            appIcon.setImageDrawable(icon)
            appTitle.text = label
        } else {
            progress = prog + 1
        }
        if (progressBar.progress < progress) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress
            progressBar.max = max
            progressBar.postInvalidate()
            progressCount.text = getString(R.string.install_count, progress, max)
            progressPercent.text = String.format("%.0f%%", ((progress * 100 / max) + 0.0f))
        }
    }

    private fun installComplete() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(installListener)
        if (Utils.isSynergyInstalled(this, "projekt.samsung.theme.compiler") && Utils.isSynergyCompatibleDevice()) {
            val intent = Intent(this, SynergySummaryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("app list", apps)
            errorMap.keys.forEach {
                if (apps.contains(it)) {
                    apps.remove(it)
                }
            }
            Holder.installApps.clear()
            Holder.installApps.addAll(apps)
            Holder.errorMap.clear()
            Holder.errorMap.putAll(errorMap)
            startActivity(intent)
        } else {
            val intent = Intent(this, InstallSummaryActivity::class.java)
            intent.putExtra("update", update)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            errorMap.keys.forEach {
                if (apps.contains(it)) {
                    apps.remove(it)
                }
            }
            Holder.installApps.clear()
            Holder.installApps.addAll(apps)
            Holder.errorMap.clear()
            Holder.errorMap.putAll(errorMap)
            swift.romHandler.postInstall(false, apps, updateAppsToUninstall, intent)
        }
        finish()
    }

    fun uninstallComplete() {
        val intent = Intent(this@InstallActivity,
                UninstallFinishedActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uninstall = intent.getBooleanExtra("uninstall", false)
        apps = SynchronizedArrayList(intent.getStringArrayListExtra("apps"))

        if (!uninstall && apps.contains("android") && !prefs.getBoolean("android_install_dialog", false)) {
            prefs.edit().putBoolean("android_install_dialog", true).apply()
            alert {
                title = getString(R.string.installing_and_uninstalling_title)
                message = getString(R.string.installing_and_uninstalling_msg)
                positiveButton(R.string.proceed) { dialog ->
                    dialog.dismiss()
                    installStart()
                }
                isCancelable = false
                show()
            }
        } else {
            installStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Utils.isSamsungOreo()) {
            if (dialog?.isShowing != true) {
                dialog?.show()
                dialog?.onRestoreInstanceState(dialogState)
                try {
                    val ai = pm.getApplicationInfo(dialogState.getString("package_name"), 0)
                    updateProgress(ai.loadLabel(pm) as String, ai.loadIcon(pm),
                            dialogState.getInt("progress"), dialogState.getInt("max"),
                            uninstall)
                } catch (e: PackageManager.NameNotFoundException) {
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!Utils.isSamsungOreo()) {
            if (dialog?.isShowing == true) {
                dialogState.putAll(dialog?.onSaveInstanceState())
                dialog?.dismiss()
            }
        }
    }

    private fun installStart() {
        update = intent.getBooleanExtra("update", false)

        if (apps.contains("android")) {
            apps.remove("android")
            apps.add("android")
        }

        val inflate = View.inflate(this, R.layout.progress_dialog_install, null)
        val builder = AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
        val fc = inflate.findViewById<TextView>(R.id.force_close)

        if (uninstall) {
            inflate.progress_dialog_title.setText(R.string.progress_uninstalling_title)
            handler.postDelayed({
                if (dialog != null && dialog?.isShowing!!) {
                    fc.visibility = View.VISIBLE
                    fc.setOnClickListener {
                        uninstallComplete()
                    }
                }
            }, 120000)
        }

        builder.setView(inflate)
        dialog = builder.create()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.setCancelable(false)

        if (!Utils.isSamsungOreo()) {
            appIcon = inflate.app_icon
            appTitle = inflate.app_name
        } else {
            inflate.app_icon.setVisible(false)
            inflate.app_name.setVisible(false)
        }
        progressBar = inflate.install_progress_bar
        progressBar.indeterminateTintList = ColorStateList.valueOf(
                swift.romHandler.getCustomizeHandler().getSelection().accentColor)
        progressBar.progressTintList = ColorStateList.valueOf(
                swift.romHandler.getCustomizeHandler().getSelection().accentColor)
        progressCount = inflate.install_progress_count
        progressPercent = inflate.install_progress_percent

        if (!uninstall) {
            if (!Utils.isSamsungOreo()) {
                if (!apps.isEmpty()) {
                    val ai = pm.getApplicationInfo(apps[0], 0)
                    dialogState.putString("package_name", apps[0])
                    dialogState.putInt("progress", 1)
                    dialogState.putInt("max", apps.size)
                    updateProgress(ai.loadLabel(pm) as String, ai.loadIcon(pm), 1, apps.size, uninstall)
                }
            } else {
                updateProgress("", null, -1, apps.size, uninstall)
            }
        } else {
            progressCount.visibility = View.INVISIBLE
            progressPercent.visibility = View.INVISIBLE
        }

        themeDialog()
        dialog?.show()

        val idleHandler = MessageQueue.IdleHandler {
            val filter = IntentFilter(Notifier.ACTION_FAILED)
            filter.addAction(Notifier.ACTION_INSTALLED)
            filter.addAction(Notifier.ACTION_INSTALL_COMPLETE)
            filter.addAction(Notifier.ACTION_UNINSTALLED)
            filter.addAction(Notifier.ACTION_UNINSTALL_COMPLETE)
            LocalBroadcastManager.getInstance(applicationContext)
                    .registerReceiver(installListener, filter)

            if (uninstall) {
                if (!ShellUtils.isRootAvailable) {
                    val intentfilter = IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                    intentfilter.addDataScheme("package")
                    registerReceiver(object : BroadcastReceiver() {
                        var count = apps.size
                        override fun onReceive(context: Context?, intent: Intent?) {
                            count--
                            if (count == 0) {
                                uninstallComplete()
                                context!!.unregisterReceiver(this)
                            }
                        }
                    }, intentfilter)
                    swift.romHandler.postInstall(uninstall = true, apps = apps)
                } else {
                    InstallerServiceHelper.uninstall(this, apps)
                }
            } else {
                InstallerServiceHelper.install(this, apps)
            }
            false
        }
        Looper.myQueue().addIdleHandler(idleHandler)
    }

    override fun recreate() {
        //super.recreate()
    }

    override fun onBackPressed() {
        // do nothing
    }

    override fun onStop() {
        super.onStop()

        if (dialog != null && dialog!!.isShowing) dialog?.cancel()
    }

    inner class InstallListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when {
                intent.action == Notifier.ACTION_INSTALLED || intent.action == Notifier.ACTION_UNINSTALLED -> {
                    val pn = intent.getStringExtra(Notifier.EXTRA_PACKAGE_NAME)
                    val label = packageManager.getApplicationInfo(pn, 0).loadLabel(packageManager)
                    val max = intent.getIntExtra(Notifier.EXTRA_MAX, 0)
                    if (!Utils.isSamsungOreo()) {
                        val icon = packageManager.getApplicationInfo(pn, 0).loadIcon(packageManager)
                        val progress = intent.getIntExtra(Notifier.EXTRA_PROGRESS, 0) + 1
                        dialogState.putString("package_name", pn)
                        dialogState.putInt("progress", progress)
                        dialogState.putInt("max", max)
                        updateProgress(label as String, icon, progress, max, uninstall)
                    } else {
                        val progress = intent.getIntExtra(Notifier.EXTRA_PROGRESS, 0)
                        updateProgress(label as String, null, progress, max, uninstall)
                    }
                }
                intent.action == Notifier.ACTION_FAILED -> {
                    errorMap[intent.getStringExtra(Notifier.EXTRA_PACKAGE_NAME)] =
                            intent.getStringExtra(Notifier.EXTRA_LOG)
                    if (update) {
                        updateAppsToUninstall.add(
                                intent.getStringExtra(Notifier.EXTRA_PACKAGE_NAME))
                    }
                }
                intent.action == Notifier.ACTION_INSTALL_COMPLETE -> {
                    installComplete()
                }
                intent.action == Notifier.ACTION_UNINSTALL_COMPLETE -> {
                    uninstallComplete()
                }
            }
        }

    }
}