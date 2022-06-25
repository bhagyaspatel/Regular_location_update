package com.example.regular_location_update

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.os.ProxyFileDescriptorCallback
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DateFormat
import java.util.*

const val PERMISSION_REQUEST_FINE_LOCATION = 0
const val REQUEST_LOCATION_SETTING = 1

//when you run the app with location off
// 1st notification is of request if of FINE_LOCATION
// 2nd is settingClient.checkLocationSettings -> onFailureListener -> when Resolution is required

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationSettingRequest : LocationSettingsRequest
    private lateinit var settingClient : SettingsClient

    private var locationUpdating : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        //we need to make some objects before starting the regular update
        //we are doing it only once in onCreate so we dont have to do it everytime we click the btn
        setupLocationUpdate()

        startBtn.setOnClickListener{
            textArea.text = ""
            locationUpdating = true

            startLocationupdates()
        }

        stopBtn.setOnClickListener{

        }
    }

    //when app goes in background
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            .addOnCompleteListener(this){

            }
    }

    private fun setupLocationUpdate() {
    //this will be run multiple times as system senses the new location
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                val currentLocation = p0.lastLocation
                val lastUpdateTime = DateFormat.getTimeInstance().format(Date())
                textArea.append(" Time $lastUpdateTime Latitude ${currentLocation.latitude} Longitude ${currentLocation.longitude}")
            }
        }

        locationRequest = LocationRequest()
        locationRequest.interval = 10000 //in mili sec
        locationRequest.fastestInterval = 5000 //if system is taking update in less than 10 sec somehow (by other app)
        //we still dont want it before 5 sec.
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        locationSettingRequest = builder.build()

        settingClient = LocationServices.getSettingsClient(this)
    }


    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)){
            //container is the id of contraint layout in xml
            val snackbar = Snackbar.make(container, R.string.location_permission_rationale, Snackbar.LENGTH_INDEFINITE)
            snackbar.setAction("OK"){
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_FINE_LOCATION)
            }
            snackbar.show()
        }else{
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_FINE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION){
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startLocationupdates()
            else
                Toast.makeText(this, "Request to permission was denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationupdates() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
            return
        }

        settingClient.checkLocationSettings(locationSettingRequest)
            //if our locationSettingRequest is compatible with our system
            .addOnSuccessListener(this) {
                fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback, Looper.myLooper()!!
                )
            }
            //if our locationSettingRequest is not compatible with our system (ex user turn off the location)
            .addOnFailureListener(this) { e ->
                when ((e as ApiException).statusCode) {
//  user turn off the location and our app dont work we ask the user to turn on. This is done by below case
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        try {
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(
                                this@MainActivity,
                                REQUEST_LOCATION_SETTING
                            ) //the result of this is captured in onActivityResult
                        } catch (sie: IntentSender.SendIntentException) {

                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Location settings are inefficient. Please Change the settings",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode){
            REQUEST_LOCATION_SETTING -> when (resultCode) {
                Activity.RESULT_OK ->{
                    //All good nothing to do
                }
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(this, "Please provide proper location settings", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }
}