# Touch Pressure/Size Gesture

## Introduction
Android OS reports pressure and/or size of your touch when you tap, press, swipe, and so on.
You can use special pressure/size value to fire any action, and this project provides interface and triggers to do that.
The triggers are new gestures by unique touching based on touch pressure/size.

The special values are

*  Large touch area -> Force Touch
*  Small touch area -> Knuckle Touch
*  Increasing touch area -> Wiggle Touch
*  Reducing touch area -> Scratch Touch.

## Xposed Module is here!
[tkgktyk/ForceTouch/xposed](xposed) is a Xposed modeule to detect Touch Pressure/Size and recognize them as Gesture.
Repository: [Force Touch Detector (FTD)](http://repo.xposed.info/module/jp.tkgktyk.xposed.forcetouchdetector).

## Force Touch
![touch2](art/touch2.png)
![touch3](art/touch3.png)

If you touch your screen with the ball of thumb, the touch area increases.
Call it "Force Touch" in this project.
It is possible to distinguish ordinary touch operation from deliberately Force Touch because there is a distinct difference between these touch area.
By checking if a touch event is started from Force Touch, you can change the action of the touch.

## Knuckle Touch
If you touch your screen with knuckle, you can make very small touch area.
The same as Force Touch, you can fire specified action when you touch on small area.

## Wiggle Touch
Above two methods use absolute threshold value to detect unique touching.
However the absolute value causes inconvenient situations, such as, you cannot fire specified action with other fingers that have not been adjusted, it is not working while charging because characteristics of touch sensor is changed by charging current on some phones.
The solution is using relative threshold.

Relative method needs more than two touch events.
When android read out initial pressure/size of each touch stroke, calculate threshold value by "initial pressure/size * ratio(variable)".
After that, when pressure/size of your touch is changed and grows over threshold, fire specified action.
The ratio must be more than 1.0 for Wiggle Touch.

## Scratch Touch
A pair method of Wiggle Touch.
When android read out initial pressure/size of each touch stroke, calculate threshold value by "initial pressure/size * ratio(variable)".
After that, when pressure/size of your touch is changed and reduces under threshold, fire specified action.
The ratio must be less than 1.0 for Scratch Touch.

## Force Touch Screen
This is an option for Wiggle Touch (available) and Scratch Touch (in the future).
The concept is to fire actions without releasing your finger like pie, and realize force touch button.

Trigger to start Force Touch Screen is Wiggle Touch or Scratch Touch.
Then FTD sends a broadcast named `FORCE_TOUCH_BEGIN` with touched position on screen.
And other actions for Force Touch Screen are also notified by broadcasting.
So any app that receives broadcast can handle this feature, such as Floating Actin.

### Broadcast Actions
Syntax of full action name is jp.tkgktyk.xposed.forcetouchdetector.intent.action._NAME_.
And all actions has package name and touch position as extra.
Package name is `jp.tkgktyk.xposed.forcetouchdetector.intent.extra.PACKAGE_NAME`, touch positions are `jp.tkgktyk.xposed.forcetouchdetector.intent.extra.X` and `jp.tkgktyk.xposed.forcetouchdetector.intent.extra.Y`.

| NAME | Parameters | Description|
|:-----|:-----------|:-----------|
|`FORCE_TOUCH_BEGIN`||Enter in Force Touch Screen.|
|`FORCE_TOUCH_DOWN`||Pressure/size passed threshold again.|
|`FORCE_TOUCH_UP`||Pressure/size reverted to initial value.|
|`FORCE_TOUCH_END`||The finger that's trigger was released.|
|`FORCE_TOUCH_CANCEL`||This touch stroke was canceled by some reason.|

## Getting Started
At first, check your hardware supports FTD functions or not.
Open a threshold screen, `Pressure -> Threshold`, and then test *tap* and *force touch* with two buttons.
If Max, Pressure and Ave are changed by each touch, your touch screen supports the pressure parameter, you can use FTD by pressure.
If not so, your phone doesn't support pressure, next try `Size -> Threshold` screen.

Note that some smartphones don't support both the pressure and the size parameter.
FTD doesn't work on such a device.

After checking the capability of your touch screen, practice and adjust Force Touch with Threshold screen.
Tap small button and do force touch on large button 5 times or more respectively, you'll get a Max value for normal tap and an Ave value for force touch.
The Ave value must be **higher** than the Max value, you should practice force touch until so.

Finally, input a number between the Ave and the Max, and turn `Master Switch` on.

## Adjustment
There are 3 parameters to adjust sensitivity for force touch.

<dl>
  <dt>Threshold</dt>
    <dd>When pressure or size of your touch exceed this value, it is recognized as a force touch.</dd>
  <dt>Detection Window</dt>
    <dd>The length of time FTD waits for force touch. If zero, FTD checks only the beginning of your touch stroke.</dd>
  <dt>Detection Sensitivity</dt>
    <dd>The sensitivity for force tap and flick. If zero, Force Touch is always interpreted as a flick. If too high value, it is always tap. The range is 0 to about 20, depends on hardware.</dd>
</dl>

I propose two strategies for adjustment.

1. *Long* detection window and *high* threshold value
1. *Short* detection window and *mid* threshold value

Maybe the experience of the former is near to Apple's Force Touch, squashing.
However Android devices detect the pressure of touch by touch area even if you enable Pressure Threshold.
In the other words, FTD's Force Touch based on Android API and Apple's one are fundamentally different.
So I recommend to try both strategies.
