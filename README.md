# Force Touch for Android
*****
## Introduction
Android devices report pressure of your touch when you tap, press, swipe, and so on.
However the pressure is different from Apple's Force Touch Technology. (That is mechanical implementation, isn't it?)
Android's pressure is depending on touch area of your finger, not strength.
But the touch area provides us new UI.

## Android's Force Touch
![touch1](art/touch1.png)

When you are holding your phone as the above image, for example, your ordinary tap operation is as below.
The blue shape shows touch area.

![touch2](art/touch2.png)

Now if you tap with the ball of your finger, the touch area increases as the following image.
Call this "Force Touch" in this project.

![touch3](art/touch3.png)

It is possible to distinguish ordinary touch operation from deliberately Force Touch because there is a distinct difference between these touch area.
By checking whether a touch event is started from Force Touch, you can change the action of the touch.

For example,

*  Open floating launcher when you do **force tap** that is a tap started from Force Touch.
*  Go back by performing **force flick to right** that is a flick started from Force Touch.
*  Expand notifications by performing **force flick to bottom** anywhere.

## Xposed Module is here!
[Force Touch Detector (FTD)](http://repo.xposed.info/module/jp.tkgktyk.xposed.forcetouchdetector) is an Xposed module to detect Force Touch and switch touch action.
FTD assigns seven force actions:

*  Tap
*  Double Tap
*  Long Press
*  Flick Left
*  Flick Right
*  Flick Up
*  Flick Down

Of course FTD doesn't bother normal operation by the threshold of pressure.
