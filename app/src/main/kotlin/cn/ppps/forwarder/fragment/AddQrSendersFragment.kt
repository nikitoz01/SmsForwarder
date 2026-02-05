package cn.ppps.forwarder.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import cn.ppps.forwarder.App
import com.alibaba.android.vlayout.VirtualLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.adapter.SenderPagingAdapter
import cn.ppps.forwarder.adapter.WidgetItemAdapter
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.database.viewmodel.BaseViewModelFactory
import cn.ppps.forwarder.database.viewmodel.SenderViewModel
import cn.ppps.forwarder.databinding.FragmentQrSendersBinding
import cn.ppps.forwarder.databinding.FragmentSendersBinding
import cn.ppps.forwarder.entity.CloneInfo
import cn.ppps.forwarder.entity.qr.jsonToSenders
import cn.ppps.forwarder.fragment.senders.BarkFragment
import cn.ppps.forwarder.fragment.senders.DingtalkGroupRobotFragment
import cn.ppps.forwarder.fragment.senders.DingtalkInnerRobotFragment
import cn.ppps.forwarder.fragment.senders.EmailFragment
import cn.ppps.forwarder.fragment.senders.FeishuAppFragment
import cn.ppps.forwarder.fragment.senders.FeishuFragment
import cn.ppps.forwarder.fragment.senders.GotifyFragment
import cn.ppps.forwarder.fragment.senders.PushplusFragment
import cn.ppps.forwarder.fragment.senders.ServerchanFragment
import cn.ppps.forwarder.fragment.senders.SmsFragment
import cn.ppps.forwarder.fragment.senders.SocketFragment
import cn.ppps.forwarder.fragment.senders.TelegramFragment
import cn.ppps.forwarder.fragment.senders.UrlSchemeFragment
import cn.ppps.forwarder.fragment.senders.WebhookFragment
import cn.ppps.forwarder.fragment.senders.WeworkAgentFragment
import cn.ppps.forwarder.fragment.senders.WeworkRobotFragment
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.KEY_SENDER_CLONE
import cn.ppps.forwarder.utils.KEY_SENDER_ID
import cn.ppps.forwarder.utils.KEY_SENDER_TYPE
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.TYPE_BARK
import cn.ppps.forwarder.utils.TYPE_DINGTALK_GROUP_ROBOT
import cn.ppps.forwarder.utils.TYPE_DINGTALK_INNER_ROBOT
import cn.ppps.forwarder.utils.TYPE_EMAIL
import cn.ppps.forwarder.utils.TYPE_FEISHU
import cn.ppps.forwarder.utils.TYPE_FEISHU_APP
import cn.ppps.forwarder.utils.TYPE_GOTIFY
import cn.ppps.forwarder.utils.TYPE_PUSHPLUS
import cn.ppps.forwarder.utils.TYPE_SERVERCHAN
import cn.ppps.forwarder.utils.TYPE_SMS
import cn.ppps.forwarder.utils.TYPE_SOCKET
import cn.ppps.forwarder.utils.TYPE_TELEGRAM
import cn.ppps.forwarder.utils.TYPE_URL_SCHEME
import cn.ppps.forwarder.utils.TYPE_WEBHOOK
import cn.ppps.forwarder.utils.TYPE_WEWORK_AGENT
import cn.ppps.forwarder.utils.TYPE_WEWORK_ROBOT
import cn.ppps.forwarder.utils.XToastUtils
import com.cassbana.barcode_qr_scanner.Format
import com.cassbana.barcode_qr_scanner.ScannerBuilder
import com.cassbana.barcode_qr_scanner.algorithm.Algorithm
import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xpage.base.XPageFragment
import com.xuexiang.xpage.core.PageOption
import com.xuexiang.xpage.enums.CoreAnim
import com.xuexiang.xpage.model.PageInfo
import com.xuexiang.xui.adapter.recyclerview.RecyclerViewHolder
import com.xuexiang.xui.utils.DensityUtils
import com.xuexiang.xui.utils.WidgetUtils
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.alpha.XUIAlphaTextView
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xutil.resource.ResUtils.getStringArray
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("PrivatePropertyName", "DEPRECATION")
@Page(name = "Senders QR")
class AddQrSendersFragment : BaseFragment<FragmentQrSendersBinding?>() {

    private val TAG: String = AddQrSendersFragment::class.java.simpleName
    private val that = this
    private var titleBar: TitleBar? = null
    private var hasAskedCameraPermission = false

    private val dialog: BottomSheetDialog by lazy { BottomSheetDialog(requireContext()) }


    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentQrSendersBinding {
        return FragmentQrSendersBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.qr_scanner)
        return titleBar
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        ensureCameraPermissionAndStart()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun startScanner() {
        binding!!.scanner.start(
            lifecycleOwner = this,
            scannerBuilder = ScannerBuilder(
                onBarcodeDetected = {
                    try {
                        val senders = jsonToSenders(it)
                        if (HttpServerUtils.restoreSettings(
                                CloneInfo(
                                    senderList = senders
                                )
                            )
                        ) {
                            MaterialDialog.Builder(requireContext())
                                .iconRes(R.drawable.icon_api_clone)
                                .title(R.string.clone)
                                .content(R.string.import_succeeded)
                                .cancelable(false)
                                .positiveText(R.string.confirm)
                                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                                    val intent = Intent(App.context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    startActivity(intent)
                                }
                                .show()
                        } else {
                            Log.e(TAG, "onBarcodeDetected: error $it")
                            XToastUtils.error(getString(R.string.qr_parse_failed))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "onBarcodeDetected: $e")
                        XToastUtils.error(getString(R.string.qr_parse_failed))
                    }
                },
                algorithm = Algorithm.MajorityOfN(50),
                stopScanningOnResult = false,
                format = Format.QR_CODE
            )
        )
    }

    private fun ensureCameraPermissionAndStart() {
        if (XXPermissions.isGrantedPermission(
                requireContext(),
                PermissionLists.getCameraPermission()
            )
        ) {
            startScanner()
            return
        }
        if (hasAskedCameraPermission) {
            return
        }
        hasAskedCameraPermission = true
        MaterialDialog.Builder(requireContext())
            .title(R.string.camera_permission_title)
            .content(R.string.camera_permission_message)
            .positiveText(R.string.confirm)
            .negativeText(R.string.cancel)
            .onPositive { _, _ ->
                requestCameraPermission()
            }
            .show()
    }

    private fun requestCameraPermission() {
        XXPermissions.with(this)
            .permission(PermissionLists.getCameraPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    val allGranted = deniedList.isEmpty()
                    if (!allGranted) {
                        val doNotAskAgain =
                            XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                        if (doNotAskAgain) {
                            XToastUtils.error(R.string.toast_denied_never)
                            XXPermissions.startPermissionActivity(requireContext(), deniedList)
                        }
                        XToastUtils.error(R.string.toast_denied)
                        return
                    }
                    startScanner()
                }
            })
    }

    override fun initListeners() {

    }


}
