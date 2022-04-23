package com.example.workwithimage

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private var bitmap:Bitmap?=null
    private lateinit var output:File
    private var photoURI:Uri?=null

    companion object{
        private const val MY_PERMISSIONS_REQUEST_CAMERA=1
        private const val MY_PERMISSIONS_REQUEST_GALLERY=2
        private const val TAG="MainActivity"
        private const val IMAGE_URL="https://static.dw.com/image/59891785_403.jpg"
        private const val IMAGE_DIRECTORY_NAME="Hello Camera"
        private const val FILE_PROVIDER_AUTHORITY="com.example.android.fileprovider"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadBtn.setOnClickListener {
            //Approach 1 : Work happens on Main Thread (It isn't ideal).
//            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitNetwork().build())
//            bitmap=downloadBitmap(IMAGE_URL)
//            imageView.setImageBitmap(bitmap)

            // Approach 2 : create a thread to do image download, then send bitmap to the main thread.
//            val uiHandler=Handler(mainLooper)
//            thread (start=true) {
//                Log.i(TAG,"Current Thread: ${Thread.currentThread().name}")
//                bitmap=downloadBitmap(IMAGE_URL)
//                uiHandler.post {
//                    Log.i(TAG,"Current Thread in UI Handler: ${Thread.currentThread().name}")
//                    imageView.setImageBitmap(bitmap)
//                }
//            }

            // Approach 3 : Coroutine
            CoroutineScope(Dispatchers.IO).launch{
                Log.i(TAG,"Current Thread: ${Thread.currentThread().name}")
                bitmap=downloadBitmap(IMAGE_URL)
                withContext(Dispatchers.Main){
                    Log.i(TAG,"Current Thread in Main Dispatcher: ${Thread.currentThread().name}")
                    imageView.setImageBitmap(bitmap)
                }
            }
        }

        cameraBtn.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                camera()
            else
                checkPermission(android.Manifest.permission.CAMERA, MY_PERMISSIONS_REQUEST_CAMERA)
        }

        galleryBtn.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED)
                gallery()
            else
                checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, MY_PERMISSIONS_REQUEST_GALLERY)
        }
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val con = URL(imageUrl).openConnection()
            con.connect()
            val inputStream = con.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        }catch (e:Exception){
            Log.e(TAG,"Exception: $e.message")
            null
        }
    }

    private fun camera(){
        val intent= Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, MY_PERMISSIONS_REQUEST_CAMERA)
    }

    private fun gallery() {
        val intent=Intent(Intent.ACTION_PICK)
        intent.type="image/*"
        startActivityForResult(intent, MY_PERMISSIONS_REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode==Activity.RESULT_OK){
            when(requestCode){
                MY_PERMISSIONS_REQUEST_CAMERA -> {
                    //Use line of code 155, 156 only if you just capturing image and set in imageview
                    val bitmap=data?.extras?.get("data") as Bitmap
                    imageView.setImageBitmap(bitmap)
                }
                MY_PERMISSIONS_REQUEST_GALLERY -> {
                    imageView.setImageURI(data?.data)
                }
            }
        }
    }

    // Returns the File for a photo stored on disk given the fileName
    fun getPhotoFileUri(fileName: String): File {
        // Get safe storage directory for photos
        // Use `getExternalFilesDir` on Context to access package-specific directories.
        // This way, we don't need to request external read/write runtime permissions.
        val mediaStorageDir =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), TAG)

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d(TAG, "failed to create directory")
        }

        // Return the file target for the photo based on filename
        return File(mediaStorageDir.path + File.separator + fileName)
    }

    //Check call permission
    private fun checkPermission(permission:String, requestCode: Int) {
        if(ContextCompat.checkSelfPermission(this,permission)!=PackageManager.PERMISSION_GRANTED){
            //Asking user when explanation is needed
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,permission)){
                //Show an explanation to the user
                //this thread wait for the user's response! after the user sees the explanation, try again to request the permission
                //Prompt the user once the explanation has been shown.
                    showRelationalDialogForPermission()
            }else{
                val s= arrayOf(permission)
                ActivityCompat.requestPermissions(this,s,requestCode)
            }
        }
    }

    private fun showRelationalDialogForPermission() {
        val msg="It seems like you have turned off permission required for this feature. It can be enable under App settings."
        AlertDialog.Builder(this)
            .setMessage(msg)
                //first _ is DialogInterface, second _ is int which(which button is clicked)
            .setPositiveButton("Go to Settings") { _, _ ->
                try{
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri= Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("cancel"){dialog,_ ->
                dialog.dismiss()
            }.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode){
            MY_PERMISSIONS_REQUEST_CAMERA -> {
                //if request is empty, the result arrays are empty
                if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    //Permission was granted. Do the contacts related task you need to do
                    if (ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        camera()
                    }
                }else{
//                    Permission denied, disable the functionality that depends on this permission
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
                }
                return
            }
            MY_PERMISSIONS_REQUEST_GALLERY -> {
                //if request is empty, the result arrays are empty
                if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    //Permission was granted. Do the contacts related task you need to do
                    if (ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        gallery()
                    }
                }else{
//                    Permission denied, disable the functionality that depends on this permission
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
                }
                return
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}