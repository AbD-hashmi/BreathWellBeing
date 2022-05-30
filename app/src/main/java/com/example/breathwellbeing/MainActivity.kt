package com.example.breathwellbeing

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.tasks.Task
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit


enum class FitActionRequestCode {
    INSERT_AND_READ_DATA
}

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() {
    private val dateFormat = DateFormat.getDateInstance()
    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)/*
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_ACTIVITY_SUMMARY, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_BODY_FAT_PERCENTAGE, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_BODY_FAT_PERCENTAGE_SUMMARY, FitnessOptions.ACCESS_READ)*/
            .build()
    }
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logView = findViewById(R.id.text)
        fitSignIn(FitActionRequestCode.INSERT_AND_READ_DATA)
    }

    private fun fitSignIn(requestCode: FitActionRequestCode) {
        if (oAuthPermissionsApproved()) {
            insertData().continueWith { readHistoryData() }
        } else {
            requestCode.let {
                GoogleSignIn.requestPermissions(
                    this,
                    requestCode.ordinal,
                    getGoogleAccount(), fitnessOptions)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            RESULT_OK -> {
                insertData().continueWith { readHistoryData() }
            }
            else -> oAuthErrorMsg()
        }
    }

    private fun oAuthErrorMsg() {
        val message = "There was an error signing into Fit. Please ask the owner of this app to grant you access for using this app, then Restart it."
        logView.text = message
    }

    private fun oAuthPermissionsApproved() =
        GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    private fun insertData(): Task<Void> {
        val dataSet = insertFitnessData()
        logView.text = getString(R.string.inserting)
        return Fitness.getHistoryClient(this, getGoogleAccount())
            .insertData(dataSet)
            .addOnSuccessListener { logView.text = "Data insert was successful!" }
            .addOnFailureListener { exception ->
                logView.text = "There was a problem inserting the dataset.$exception"
            }
    }

    private fun readHistoryData(): Task<DataReadResponse> {
        val readRequest = queryFitnessData()

        return Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(readRequest)
            .addOnSuccessListener { dataReadResponse ->
                printData(dataReadResponse)
            }
            .addOnFailureListener { e ->
                logView.text = "There was a problem reading the data.$e"
            }

    }

    private fun insertFitnessData(): DataSet {
        logView.text = "Creating a new data insert request."

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        val startTime = calendar.timeInMillis

        val dataSource = DataSource.Builder()
            .setAppPackageName(this)
            .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .setStreamName("step count")
            .setType(DataSource.TYPE_RAW)
            .build()

        val stepCountDelta = 950
        return DataSet.builder(dataSource)
            .add(DataPoint.builder(dataSource)
                .setField(Field.FIELD_STEPS, stepCountDelta)
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()
            ).build()
    }

    private fun queryFitnessData(): DataReadRequest {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        val startTime = calendar.timeInMillis

        logView.text = "Range Start: ${dateFormat.format(startTime)}"
        logView.text = "Range End: ${dateFormat.format(endTime)}"

        return DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)/*
            .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
            .aggregate(DataType.TYPE_BODY_FAT_PERCENTAGE,
                DataType.AGGREGATE_BODY_FAT_PERCENTAGE_SUMMARY)*/

            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun printData(dataReadResult: DataReadResponse) {
        if (dataReadResult.buckets.isNotEmpty()) {
            logView.text =
                "Number of returned buckets of DataSets is: \t" + dataReadResult.buckets.size
            val builderStr: StringBuilder = StringBuilder()
            for (bucket in dataReadResult.buckets) {
                bucket.dataSets.forEach { builderStr.append(dumpDataSetOnScreen(it)) }
            }
            logView.text = builderStr.toString()

        } else if (dataReadResult.dataSets.isNotEmpty()) {
            logView.text = "Number of returned DataSets is:\t " + dataReadResult.dataSets.size
            dataReadResult.dataSets.forEach { dumpDataSetOnScreen(it) }
        }
    }

    private fun dumpDataSetOnScreen(dataSet: DataSet): String {
        var str = ""
        str += "\n\nData returned for Data type: ${dataSet.dataType.name}"

        for (dp in dataSet.dataPoints) {
            str += "Data point:"
            str += "\n\tType: ${dp.dataType.name}"
            str += "\n\tStart: ${dp.getStartTimeString()}"
            str += "\n\tEnd: ${dp.getEndTimeString()}"
            dp.dataType.fields.forEach {
                str += "\n\tField: ${it.name} Value: ${dp.getValue(it)}\n"
            }
        }
        return str
    }
}