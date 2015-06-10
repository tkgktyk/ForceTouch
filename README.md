# Force Touch for Android

## Introduction
Android reports pressure of your touch when you tap, press, swipe, and so on.
However the pressure is different from Apple's Force Touch Technology. (It is mechanical implementation, isn't it?)
**Android's pressure is depending on touch area of your finger, not strength.**
But the touch area provides us new UI.

## Force Touch on Android
![touch2](art/touch2.png)
![touch3](art/touch3.png)

If you touch with the ball of thumb, the touch area increases.
Call it "Force Touch" in this project.
It is possible to distinguish ordinary touch operation from deliberately Force Touch because there is a distinct difference between these touch area.
By checking whether a touch event is started from Force Touch, you can change the action of the touch.

Examples:

*  Open launcher when you do **force tap** that is a tap started from Force Touch.
*  Go back by performing **force flick to right** that is a flick started from Force Touch.
*  Expand notifications by performing **force flick to bottom** anywhere.

## Xposed Module is here!
[tkgktyk/ForceTouch/xposed](xposed) is an Xposed modeule to detect Force Touch and switch touch action.
Repository is here: [Force Touch Detector (FTD)](http://repo.xposed.info/module/jp.tkgktyk.xposed.forcetouchdetector).

FTD assigns seven force actions:

*  Tap
*  Double Tap
*  Long Press
*  Flick Left
*  Flick Right
*  Flick Up
*  Flick Down

Of course FTD doesn't bother normal operation by the threshold of pressure.
