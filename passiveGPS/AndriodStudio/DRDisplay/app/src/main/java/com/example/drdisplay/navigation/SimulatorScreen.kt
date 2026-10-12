package com.example.drdisplay.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import com.example.drdisplay.navigation.deadReckoning.ImuSimulator
import com.example.drdisplay.navigation.deadReckoning.DeadReckoning


@Composable
fun SimulatorScreen() {
    var x by remember { mutableStateOf(0.0) }
    var y by remember { mutableStateOf(0.0) }
    var heading by remember { mutableStateOf(0.0) }
    var simTime by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        val dr = DeadReckoning()
        dr.calibrate()
        var t = 0.0
        while (true) {
            val sample =
                ImuSimulator.generateWalkingData(t)
            val state =
                dr.update(
                    ax = sample.ax,
                    ay = sample.ay,
                    az = sample.az,
                    gx = sample.gx,
                    gy = sample.gy,
                    gz = sample.gz,
                    dt = 0.02
                )
            x = state.x
            y = state.y
            heading = state.heading
            simTime = t
            t += 0.02
            delay(500)
        }
    }
    Column {
        Text("X = %.4f".format(x))
        Text("Y = %.4f".format(y))
        Text("Heading = %.4f".format(heading))
        Text("Sim Time = %.2f s".format(simTime))
    }
}