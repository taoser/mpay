package com.weihuagu.receiptnotice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Path;
import android.os.Build;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.jeremyliao.liveeventbus.LiveEventBus;
import com.weihuagu.receiptnotice.filteringmiddleware.AlipayTransferBean;
import com.weihuagu.receiptnotice.util.LogUtil;
import com.weihuagu.receiptnotice.util.message.MessageConsumer;
import com.weihuagu.receiptnotice.util.message.MessageSendBus;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ReceiptnoticeAccessibilityService extends AccessibilityService implements MessageConsumer {
    PowerManager pm=null;
    String TAG="onAccessibilityEvent";
    private PowerManager.WakeLock mWakeLock = null;
    private KeyguardManager mKeyguardManager;
    private KeyguardManager.KeyguardLock kl;
    private String lastpoststr = "";
    private String lastnotistr = "";
    private Queue<String> poststrqueue = new LinkedList<String>();
    private void setLastPostStr(String str){
        lastpoststr=str;
    }

    private void setLastNotiStr(String str){
        lastnotistr=str;
    }
    @Override
    public void onServiceConnected(){
        debugLogWithDeveloper("accessibility service connected");
        subMessage();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        kl = mKeyguardManager.newKeyguardLock("myapp:kllock");



    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        debugLogWithDeveloper( event.toString());
        final int eventType = event.getEventType();
        //????????????????????????????????????
        switch (eventType) {
            //???????????????????????????
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:

                break;
            //?????????????????????????????????
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                String className = event.getClassName().toString();
                getAlipayTransferInfo(className);
                break;
        }


    }

    @Override
    public void onInterrupt() {
        debugLogWithDeveloper(  "oninterrupt");
    }

    private void mockSwipe(){
        if(Build.VERSION.SDK_INT >= 24) {
            //???????????????????????????
            WindowManager wm = (WindowManager) MainApplication.getAppContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            int width = dm.widthPixels;
            int height = dm.heightPixels;
            int cx = width/2;
            int cy = height / 2;
            final Path path = new Path();

            path.moveTo(cx, cy); //????????????????????????????????????????????????X???Y
            path.lineTo(cx, 0); //????????????????????????????????????????????????????????????
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription gestureDescription = builder.addStroke(
                    new GestureDescription.StrokeDescription(path, 100, 400)
            ).build(); //?????????????????????100ms???????????????????????????????????????400ms???????????????
            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                //???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "swipe  success.");
                    path.close();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, " swipe  fail.");
                    path.close();
                }
            },null);
        }
    }


    public void subMessage(){
        LiveEventBus
                .get("action_request_return", String.class)
                .observeForever( new Observer<String>() {
                    @Override
                    public void onChanged(@Nullable String s) {
                        LogUtil.debugLog("??????????????????:action_request_return " + s);
                        if(s.equals("return")){
                            performGlobalAction(GLOBAL_ACTION_BACK);

                        }
                    }
                });
        LiveEventBus
                .get("action_request_home", String.class)
                .observeForever( new Observer<String>() {
                    @Override
                    public void onChanged(@Nullable String s) {
                        LogUtil.debugLog("??????????????????:action_request_home " + s);
                        if(s.equals("home")){
                            performGlobalAction(GLOBAL_ACTION_HOME);

                        }
                    }
                });
        LiveEventBus
                .get("message_noti_alipay_transfer_arrive", String.class)
                .observeForever( new Observer<String>() {
                    @Override
                    public void onChanged(@Nullable String s) {
                        LogUtil.debugLog("??????????????????:message_noti_alipay_transfer_arrive " + s);
                        wakeAndUnlock(true);
                        setLastNotiStr(s);

                    }
                });

        LiveEventBus
                .get("update_laststr", String.class)
                .observeForever( new Observer<String>() {
                    @Override
                    public void onChanged(@Nullable String s) {
                        LogUtil.debugLog("??????????????????:update_laststr " + s);
                        poststrqueue.offer(s);
                        setLastPostStr(s);
                    }
                });

    }

    public void getAlipayTransferInfo(String classname){

        String transnumid="com.alipay.mobile.chatapp:id/biz_desc";
        String transremarkid="com.alipay.mobile.chatapp:id/biz_title";
        debugLogWithDeveloper( ":??????????????????,?????????"+classname);
        if(classname.equals("com.alipay.mobile.chatapp.ui.PersonalChatMsgActivity_")){
            mockSwipe();
            AccessibilityNodeInfo nodepersonalchat=null;
            AccessibilityWindowInfo windowInfopersonalchat=null;
            if(pm.isScreenOn()) {
                 nodepersonalchat= getRootInActiveWindow();
            }else {
                if(Build.VERSION.SDK_INT >= 21) {
                    windowInfopersonalchat = getWindows().get(1);
                    nodepersonalchat=windowInfopersonalchat.getRoot();
                }
            }
            if (nodepersonalchat== null) {
                return;
            }
            // ?????????????????????????????????
            try {
                List<AccessibilityNodeInfo> list = nodepersonalchat.findAccessibilityNodeInfosByViewId(transnumid);
                AccessibilityNodeInfo thelastnode= list.get(list.size() - 1);
                String transnum = thelastnode.getText().toString();
                List<AccessibilityNodeInfo> remarklist=thelastnode.getParent().findAccessibilityNodeInfosByViewId(transremarkid);
                String transremark=remarklist.get(remarklist.size()-1).getText().toString();
                debugLogWithDeveloper(":?????????" + transnum);
                debugLogWithDeveloper(":?????????" + transremark);
                AlipayTransferBean transferbean=new AlipayTransferBean();
                transferbean.setNum(transnum);
                transferbean.setRemark(transremark);
                if(!poststrqueue.poll().equals(lastnotistr))
                MessageSendBus.postMessageWithget_alipay_transfer_money(transferbean);
            }catch (ArrayIndexOutOfBoundsException e){

            }
        }

    }

    public void debugLogWithDeveloper(String info){
        Log.d(TAG,info);
    }

    /**
     * ?????????????????????
     * @param unLock ??????????????????
     */
    private void wakeAndUnlock(boolean unLock)
    {
        if(unLock)
        {
            //?????????????????????????????????
            if(!pm.isScreenOn()) {
                //??????????????????????????????ACQUIRE_CAUSES_WAKEUP????????????????????????????????????
                mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "myapp:bright");
                //????????????
                mWakeLock.acquire();
                kl.disableKeyguard();
                Log.i("QHB", "??????");
            }
        }
        else
        {
            //?????????????????????????????????????????????????????????
            if(mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;
                Log.i("QHB", "??????");
            }
        }
    }
}
