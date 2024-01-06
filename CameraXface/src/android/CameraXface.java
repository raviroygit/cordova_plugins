package com.dipoletechi.cameraXface;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cordova.PluginResult;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import android.view.ViewGroup;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.core.content.ContextCompat;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import android.media.Image;
import com.google.mlkit.vision.common.InputImage;
import android.annotation.SuppressLint;
import android.util.Size;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

public class CameraXface extends CordovaPlugin {

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private volatile boolean isRunning = false;
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("startCamera")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startCamera(callbackContext);
                }
            });
            return true;
        }
        if (action.equals("getFrameData")) {
                    getFrameData(callbackContext);
            return true;
        }
        return false;
    }



    private void startCamera(CallbackContext callbackContext) {
        previewView = new PreviewView(cordova.getActivity().getApplicationContext());
        previewView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cordova.getActivity().setContentView(previewView);

        cameraProviderFuture = ProcessCameraProvider.getInstance(cordova.getActivity());
cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreviewUseCase(callbackContext);
            } catch (Exception e) {
                callbackContext.error("Error initializing CameraX");
            }
        }, ContextCompat.getMainExecutor(cordova.getActivity()));
    }

 private void getFrameData (CallbackContext callbackContext){
            cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreviewUseCase(callbackContext);
            } catch (Exception e) {
                callbackContext.error("Error initializing CameraX");
            }
        }, ContextCompat.getMainExecutor(cordova.getActivity()));
}

    private void bindPreviewUseCase(CallbackContext callbackContext) {
        isRunning = true;
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Executor executor = Executors.newSingleThreadExecutor();

            ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
                        .build();

            cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                         while (isRunning) {
                            // try {
                            //          Thread.sleep(intervalMillis);
                            // } catch (Exception e) {
                            //             e.printStackTrace();
                            //     }
                    
                    imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
                        @Override
                        public void analyze(@NonNull ImageProxy imageProxy) {

                            try {
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                 callbackContext.error("Image analyzing failed");
                                }

                                  InputImage image = null;

                                    @SuppressLint({"UnsafeExperimentalUsageError", "UnsafeOptInUsageError"})

                                    Image mediaImage = imageProxy.getImage();

                                    if (mediaImage != null) {
                                        image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                                    }
                                   byte[] imgByte = YUV_420_888toNV21(mediaImage);
                                     callbackContext.success("Buffer data from image: "+String.valueOf(imgByte));
                                    imageProxy.close();

                        }}); 
                       }               
                    }           
                });

                       
        camera = cameraProvider.bindToLifecycle(cordova.getActivity(), cameraSelector,imageAnalysis, preview);
    }  


    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
            }
            vBuffer.put(1, savePixel);
        }

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }
        return nv21;
    } 

}
