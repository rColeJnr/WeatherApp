package com.rick.weatherapp

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.rick.weatherapp.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        binding.fab.setOnClickListener{
            getLocation()
        }
        binding.root.setOnRefreshListener {
            refreshData()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.root.isRefreshing = true
        refreshData()
    }

    private fun refreshData() {
        when(val location = sharedPreferences.getString("location", null)) {
            null, "currentLocation" -> getLocation()
            else -> updateWeatherData("$CITY_NAME_URL$location")
        }
        binding.root.isRefreshing = false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId){
            R.id.refresh -> {binding.root.isRefreshing = true; refreshData()}
            R.id.change_city -> showInputDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showInputDialog() {
        val input = EditText(this@MainActivity)
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.change_city))
            setView(input)
            setPositiveButton(getString(R.string.go)){ _,_ ->
                val city = input.text.toString()
                updateWeatherData("$CITY_NAME_URL$city")
                sharedPreferences.edit().apply{
                    putString("location", city)
                    apply()
                }
            }
            show()
        }
    }

    private fun getLocation(){
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val apiCall = "$GEO_COORDINATES_URL${location.latitude}&lon=${location.longitude}"
                    updateWeatherData(apiCall)
                    sharedPreferences.edit().apply{
                        putString("location", "currentLocation")
                        apply()
                    }
                }
            }
        }
    }

    private fun updateWeatherData(apiCall: String) {
        object : Thread() {
            override fun run() {
                val jsonObject = getJson(apiCall)
                runOnUiThread {
                    if (jsonObject != null) renderWeather(jsonObject)
                    else Toast.makeText(this@MainActivity, getString(R.string.data_not_found), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun renderWeather(jsonObject: JSONObject) {
        try {
            val city = jsonObject.getString("name").uppercase(Locale.US)
            val country = jsonObject.getJSONObject("sys").getString("country")
            binding.txtCity.text = resources.getString(R.string.city_field, city, country)

            val weatherDetails = jsonObject.optJSONArray("weather")?.getJSONObject(0)
            val main = jsonObject.getJSONObject("main")
            val description = weatherDetails?.getString("description")
            val humidity = main.getString("humidity")
            val pressure = main.getString("pressure")
            binding.txtDetails.text = resources.getString(R.string.details_field, description, humidity, pressure)

            val iconId = weatherDetails?.getString("icon") ?: "03d"
            val urlString = "https://openweathermap.org/img/w/$iconId.png"

            Glide.with(this)
                .load(urlString)

                .into(binding.imgWeatherIcon)

            val temperature = main.getDouble("temp")

            binding.txtTemperature.text = resources.getString(R.string.temperature_field, temperature)

            val df = DateFormat.getDateTimeInstance()
            val lastUpdated = df.format(Date(jsonObject.getLong("dt")* 1000))
            binding.txtUpdated.text = resources.getString(R.string.updated_field, lastUpdated)
        }catch (ignore: Exception) {}
    }

    private fun getJson(apiCall: String): JSONObject? {
        try {
            val con = URL("$apiCall&appid=$APP_ID&units=metric").openConnection() as HttpURLConnection
            con.apply {
                doOutput = true //true to indicate that we will use the connection to request data.
                connect()
            }
            val inputStream = con.inputStream
            val br = BufferedReader(InputStreamReader(inputStream!!))
            var line: String?
            val buffer = StringBuffer()
            while (br.readLine().also { line = it } != null) buffer.append(line + "\n")
            inputStream.close()
            con.disconnect()

            val jsonObject = JSONObject(buffer.toString())
            Log.w("josf", "response:  ${jsonObject.get("main")}")
            return if (jsonObject.getInt("cod") != 200) null
            else jsonObject
        } catch (t: Throwable) {return null}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation()
        else if (requestCode == 1 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val APP_ID = "3166b38070770ce412d906017f55bf84"
        private const val CITY_NAME_URL = "https://api.openweathermap.org/data/2.5/weather?q="
        private const val GEO_COORDINATES_URL = "https://api.openweathermap.org/data/2.5/weather?lat="

    }
}