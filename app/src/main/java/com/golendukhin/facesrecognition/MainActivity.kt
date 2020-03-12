package com.golendukhin.facesrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.golendukhin.facesrecognition.databinding.MainActivityBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions

private const val CAMERA_PERMISSION_REQUEST_CODE = 101
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var binding: MainActivityBinding
    private lateinit var previewView: PreviewView
    private lateinit var imagePreview: Preview
    private lateinit var firebaseVisionFaceDetector: FirebaseVisionFaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        previewView = binding.previewView

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        if (hasCameraPermissions()) previewView.post {startCamera() }
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE)

        supportActionBar?.hide()

        initFireBaseDetection()
    }

    private fun initFireBaseDetection() {
        val firebaseVisionFaceDetectorOptions : FirebaseVisionFaceDetectorOptions =
            FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
            .setLandmarkMode(FirebaseVisionFaceDetectorOptions.NO_LANDMARKS)
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
            .build()

        firebaseVisionFaceDetector = FirebaseVision.getInstance().getVisionFaceDetector(firebaseVisionFaceDetectorOptions)
    }

    private fun startCamera() {
        imagePreview = Preview.Builder().apply {
            setTargetAspectRatio(AspectRatio.RATIO_16_9)
            setTargetRotation(previewView.display.rotation)
        }.build()
        imagePreview.setSurfaceProvider(previewView.previewSurfaceProvider)

        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.bindToLifecycle(this, cameraSelector, imagePreview)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun hasCameraPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (hasCameraPermissions()) previewView.post { startCamera() }
            else finish()
        }
    }

    inner class ImageProcessor : ImageAnalysis.Analyzer {
        private val TAG = javaClass.simpleName

        private fun degreesToFirebaseRotation(degrees: Int): Int = when(degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            imageProxy.image?.let {
                // Extract the mediaImage from the ImageProxy
                val visionImage : FirebaseVisionImage = FirebaseVisionImage.fromMediaImage(it, rotationDegrees)
                firebaseVisionFaceDetector.detectInImage(visionImage)
                    .addOnSuccessListener { faces ->
                        // Returns an array containing all the detected faces
                        faces.forEach { face ->
                            // Loop through the array and detect features
                            Log.e(TAG, "Smiling probability ${face.smilingProbability}")
                            Log.e(TAG, "Left eye open probability ${face.leftEyeOpenProbability}")
                            Log.e(TAG, "Right eye open probability ${face.rightEyeOpenProbability}")
                            Log.e(TAG, "Tracking id ${face.trackingId}")
                        }
                    }
                    .addOnFailureListener {
                        it.printStackTrace()
                    }
            }
        }
    }

    private class YourImageAnalyzer : ImageAnalysis.Analyzer {
        private fun degreesToFirebaseRotation(degrees: Int): Int = when(degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }

        override fun analyze(imageProxy: ImageProxy?, degrees: Int) {
            val mediaImage = imageProxy?.image
            val imageRotation = degreesToFirebaseRotation(degrees)
            if (mediaImage != null) {
                val image = FirebaseVisionImage.fromMediaImage(mediaImage, imageRotation)
                // Pass image to an ML Kit Vision API
                // ...
            }
        }
    }
}