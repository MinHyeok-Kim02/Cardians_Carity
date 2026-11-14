package com.example.pleostest

/** Local demonstration only: no Context, SDK, HTTP or vehicle writes. */
class VirtualKeyboardDrive {
    data class Input(val speedMps: Double,val steering: Double,val brake: Boolean)
    private val model=ConsoleDriveModel()
    private var command=ConsoleDriveModel.Command(0.0,0,0,0)
    val gear get()=model.gear

    fun reset() {
        model.clear(); model.select(ConsoleDriveModel.Gear.P,0.0)
        command=ConsoleDriveModel.Command(0.0,0,0,0)
    }
    fun stop() { model.clear(); command=ConsoleDriveModel.Command(0.0,0,100,0) }
    fun select(value: ConsoleDriveModel.Gear)=model.select(value,command.speedKph/3.6)
    fun press(control: AlleyKeyboardDriver.Control)=model.press(control)
    fun release(control: AlleyKeyboardDriver.Control)=model.release(control)
    fun advance(seconds: Double) { command=model.tick(seconds) }
    fun input()=Input(
        VehicleMotionInput.signedSpeed(command.speedKph/3.6,gear)!!,
        command.steering/500.0,command.brake>0)
}
