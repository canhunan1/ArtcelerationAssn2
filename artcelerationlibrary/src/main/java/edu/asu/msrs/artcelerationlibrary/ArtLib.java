package edu.asu.msrs.artcelerationlibrary;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.MemoryFile;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by rlikamwa on 10/2/2016.
 */

public class ArtLib {
    private TransformHandler artlistener;
    private Activity mActivity;
    private Messenger mMessenger;
    private boolean mBound;
    static final int MSG_HELLO = 0;
    static final int MSG_MULTI = 1;

    public ArtLib(Activity activity) {
        mActivity = activity;
        init();
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                   // mCallbackText.setText("Received from service: " + msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger inMessenger = new Messenger(new IncomingHandler());

    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMessenger = new Messenger(service);
            mBound = true;

            try {
                Message msg = Message.obtain(null,
                        1);
                msg.replyTo = inMessenger;
                mMessenger.send(msg);

                // Give it some value as an example.
                msg = Message.obtain(null,
                        6, this.hashCode(), 0);
                Log.d("ddd",String.valueOf(msg.what));
                mMessenger.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMessenger = null;
            mBound = false;
        }
    };

    public void init() {
        mActivity.bindService(new Intent(mActivity, TransformService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public String[] getTransformsArray() {
        String[] transforms = {"Gaussian Blur", "Neon edges", "Color Filter"};
        return transforms;
    }

    public TransformTest[] getTestsArray() {
        TransformTest[] transforms = new TransformTest[3];
        transforms[0] = new TransformTest(0, new int[]{1, 2, 3}, new float[]{0.1f, 0.2f, 0.3f});
        transforms[1] = new TransformTest(1, new int[]{11, 22, 33}, new float[]{0.3f, 0.2f, 0.3f});
        transforms[2] = new TransformTest(2, new int[]{51, 42, 33}, new float[]{0.5f, 0.6f, 0.3f});

        return transforms;
    }

    public void registerHandler(TransformHandler artlistener) {
        this.artlistener = artlistener;
    }

    public boolean requestTransform(Bitmap img, int index, int[] intArgs, float[] floatArgs) {
        try {
            //Write the image to the memory file
            //Firstly,convert bitmap to byte array
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            img.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            //Secondly, put the stream into the memory file.
            MemoryFile memoryFile = new MemoryFile("someone", byteArray.length);
            memoryFile.writeBytes(byteArray, 0, 0, byteArray.length);
            ParcelFileDescriptor pfd = MemoryFileUtil.getParcelFileDescriptor(memoryFile);
            //memoryFile.close();
            int what = MSG_MULTI;
            Bundle dataBundle = new Bundle();
            dataBundle.putParcelable("pfd", pfd);
            Message msg = Message.obtain(null, what, 2, 3);
            msg.setData(dataBundle);


            //when the transform finished, trigger the callback function
            if(artlistener != null) {
                artlistener.onTransformProcessed(img);
            }
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;


    }
}
