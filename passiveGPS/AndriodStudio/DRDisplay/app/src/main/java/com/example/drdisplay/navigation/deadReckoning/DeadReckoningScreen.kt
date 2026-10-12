package com.example.drdisplay.navigation.deadReckoning

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.remember
import com.example.drdisplay.navigation.deadReckoning.DeadReckoning

@Composable
fun DeadReckoningScreen() {
    val dr = remember {
        DeadReckoning()
    }
    dr.calibrate()
    repeat(100) {
        dr.update(
            ax = 1.0,
            ay = 0.0,
            az = 9.81,
            gx = 0.0,
            gy = 0.0,
            gz = 0.0,
            dt = 0.02
        )
    }
    val state = dr.getState()
    Column {
        Text("Dead Reckoning")
        Text("X: %.10f".format(state.x))
        Text("Y: %.10f".format(state.y))
        Text("Z: %.10f".format(state.z))
        Text("Heading: %.10f".format(state.heading))
        Text("VX: %.10f".format(state.vx))
        Text("VY: %.10f".format(state.vy))
        Text("VZ: %.10f".format(state.vz))
        Text("Roll: %.10f".format(state.roll))
        Text("Pitch: %.10f".format(state.pitch))
        Button(
            onClick = {
                println("Reset pressed")
            }
        ) {
            Text("Reset")
        }
    }
}