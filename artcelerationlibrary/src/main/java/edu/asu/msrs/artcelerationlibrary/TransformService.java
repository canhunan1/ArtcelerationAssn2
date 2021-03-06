/*
* This class is used to do the background service.
* When doing image transform, just put the image into a new thread with the async.
* After the processing, send the image back to the front by message.
* */

package edu.asu.msrs.artcelerationlibrary;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.MemoryFile;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TransformService extends Service {

    static final int COLOR_FILTER = 0;
    static final int MOTION_BLUR = 1;
    static final int GAUSSIAN_BLUR = 2;
    static final int SOBEL_EDGE = 3;
    static final int NEON_EDGES = 4;
    static final int TEST_TRANS = 5;
    static final String TAG = "ArtTransformService";
    static ArrayList<Messenger> mClients = new ArrayList<>();
    static Messenger replyTo;
    static int TransformType;

    public TransformService() {
    }

    private class TransformPackage{
        Bitmap img;
        int[] intArgs;
        float[] floatArgs;

    }

    /*
    * This class defines what to do after got a message from library
    * */
    class ArtTransformHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            replyTo = msg.replyTo;
            TransformType = msg.what;
            try {
                new AsyncTest().execute(getBitmap(msg));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    /*
    * This method is to get the bitmap from the message
    * @param msg
    * @return       the mutable bitmap so that it can be modified
    * */
    private TransformPackage getBitmap(Message msg) throws IOException {
        TransformPackage tP = new TransformPackage();
        Bundle dataBundle = msg.getData();
        ParcelFileDescriptor pfd = (ParcelFileDescriptor) dataBundle.get("pfd");
        InputStream istream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        //Convert the istream to bitmap
        byte[] byteArray = IOUtils.toByteArray(istream);
        //The configuration is ARGB_8888, if the configuration changed in the application, here should be changed
        // a better way is to pass the parameter through the message.
        Bitmap.Config configBmp = Bitmap.Config.valueOf("ARGB_8888");
        Bitmap img = Bitmap.createBitmap(msg.arg1, msg.arg2, configBmp);
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        img.copyPixelsFromBuffer(buffer);
        int[] intArgs = dataBundle.getIntArray("intArgs");
        float[] floatArgs = dataBundle.getFloatArray("floatArgs");

        dataBundle.putFloatArray("floatArgs", floatArgs);
        tP.img = img;
        tP.intArgs = intArgs;
        tP.floatArgs = floatArgs;
        return tP;
    }

    /*
    * This method is used to send the processed image back to the activity
    * @param msg
    * @param img    the image which has been processed
    * */
    private void imageProcessed(Bitmap img) {
        if(img == null)
            return;

        int width = img.getWidth();
        int height = img.getHeight();
        int what = 0;
        Message msg = Message.obtain(null, what, width, height);
        msg.replyTo = replyTo;

        //Message msg = Message.obtain(null, what);
        Bundle dataBundle = new Bundle();
        mClients.add(msg.replyTo);
        if (msg.replyTo == null) {
            Log.d("mclient is ", "null");
        }
        try {
            int bytes = img.getByteCount();
            ByteBuffer buffer = ByteBuffer.allocate(bytes); //Create a new buffer
            img.copyPixelsToBuffer(buffer); //Move the byte data to the buffer
            byte[] byteArray = buffer.array();

            /*ByteArrayOutputStream stream = new ByteArrayOutputStream();
            img.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();*/
            //Secondly, put the stream into the memory file.
            MemoryFile memoryFile = new MemoryFile("someone", byteArray.length);
            memoryFile.writeBytes(byteArray, 0, 0, byteArray.length);
            ParcelFileDescriptor pfd = MemoryFileUtil.getParcelFileDescriptor(memoryFile);
            memoryFile.close();
            dataBundle.putParcelable("pfd", pfd);
            msg.setData(dataBundle);
            //msg.obtain(null,6, 2, 3);
            mClients.get(0).send(msg);
        } catch (RemoteException | IOException e) {
            e.printStackTrace();
        }
    }

    /*
    * This method is the test transform which change part of the image to be yellow
    * @param img    img should be mutable bitmap
    * @return img   processed image
    * */
    private Bitmap testTransform(Bitmap img) {
        int width = img.getWidth();
        int height = img.getHeight();

        for (int x = width / 4; x < width / 2; x++) {
            for (int y = height / 4; y < height / 2; y++) {
                img.setPixel(x, y, Color.BLUE);
//                int thisColor = img.getPixel(x, y);
//                Log.d("the red value is ", String.valueOf(Color.red(thisColor)));
//                Log.d("the blue value is ", String.valueOf(Color.blue(thisColor)));
//                Log.d("the green value is ", String.valueOf(Color.green(thisColor)));
            }
        }
        return img;
    }


    final Messenger mMessenger = new Messenger(new ArtTransformHandler());

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }


    class AsyncTest extends AsyncTask<TransformPackage, Float, Bitmap> {
        //DONE IN BACKGROUND
        /*
        * This function is used to do image transform in a separated thread.
        * @param tp       tP is a TransformPackage array
        * @return Bitmap  the result of after the transformation
        * */
        @Override
        protected Bitmap doInBackground(TransformPackage... tP) {

            Bitmap img = null;
            switch (TransformType) {
                case COLOR_FILTER:
                    NativeTransform n = new NativeTransform();
                    n.colorFilter(tP[0].img, tP[0].intArgs);
                    img = tP[0].img;
                    Log.d("Finished","COLOR_FILTER");
                    break;
                case MOTION_BLUR:
                    NativeTransform m = new NativeTransform();
                    m.motionBlur(tP[0].img,tP[0].intArgs);
                    img = tP[0].img;
                    Log.d("Finished","MOTION_BLUR");
                    break;
                case GAUSSIAN_BLUR:
                    GaussianBlur gaussianBlur=new GaussianBlur(tP[0].img, tP[0].intArgs,tP[0].floatArgs);
                    img =  gaussianBlur.startTransform();
                    Log.d("Finished","GAUSSIAN_BLUR");
                    break;
                case SOBEL_EDGE:
                    SobelEdgeFilter sobelEdgeFilter=new SobelEdgeFilter(tP[0].img,tP[0].intArgs[0]);
                    img = sobelEdgeFilter.startTransform();
                    Log.d("Finished","SOBEL_EDGE");
                    break;
                case NEON_EDGES:
                    NeonEdge.NeonEdgeTransForm(tP[0].img,tP[0].floatArgs);
                    img = NeonEdge.NeonEdgeTransForm(tP[0].img,tP[0].floatArgs);
                    Log.d("Finished","NEON_EDGES");
                    break;
                default:
                    break;
            }
            return img;
        }
        //ON UI THREAD
        protected void onPostExecute(Bitmap mutableBitmap) {

            imageProcessed(mutableBitmap);
            Log.d("AsyncTest", "AsyncTest finished");
        }
    }

}
