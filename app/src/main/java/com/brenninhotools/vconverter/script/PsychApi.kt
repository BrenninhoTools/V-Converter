package com.brenninhotools.vconverter.script

object PsychApi {
    val constants = mapOf(
        "Function_Stop" to "##PSYCHLUA_FUNCTIONSTOP",
        "Function_Continue" to "##PSYCHLUA_FUNCTIONCONTINUE",
        "Function_StopLua" to "##PSYCHLUA_FUNCTIONSTOPLUA",
        "Function_StopHScript" to "##PSYCHLUA_FUNCTIONSTOPHSCRIPT",
        "Function_StopAll" to "##PSYCHLUA_FUNCTIONSTOPALL"
    )

    val variables = setOf(
        "altAnim", "botPlay", "boyfriendName", "bpm", "buildTarget", "cameraX", "cameraY", "cameraZoomOnBeat", "chartPath", "crochet",
        "curBeat", "curBpm", "curDecBeat", "curDecStep", "curSection", "curStage", "curStep", "currentModDirectory", "dadName", "deaths",
        "defaultBoyfriendX", "defaultBoyfriendY", "defaultGirlfriendX", "defaultGirlfriendY", "defaultOpponentX", "defaultOpponentY",
        "difficulty", "difficultyName", "difficultyNameTranslation", "difficultyPath", "downscroll", "flashingLights", "framerate",
        "gfName", "gfSection", "ghostTapping", "guitarHeroSustains", "hasVocals", "healthBarAlpha", "healthGainMult", "healthLossMult",
        "hideHud", "hits", "inGameOver", "instakillOnMiss", "isStoryMode", "loadedSongName", "loadedSongPath", "lowQuality", "luaDebugMode",
        "luaDeprecatedWarnings", "middlescroll", "misses", "modFolder", "mustHitSection", "noResetButton", "noteOffset", "noteSkin",
        "noteSkinPostfix", "playbackRate", "practice", "rating", "ratingFC", "ratingName", "score", "scoreZoom", "screenHeight",
        "screenWidth", "scriptName", "scrollSpeed", "seenCutscene", "shadersEnabled", "songLength", "songName", "songPath",
        "songPosition", "splashAlpha", "splashSkin", "splashSkinPostfix", "startedCountdown", "timeBarType", "totalNotesHit",
        "totalPlayed", "version", "week", "weekRaw", "combo"
    )

    private val strumVariable = Regex("^default(Player|Opponent)Strum[XY][0-9]$")

    fun isVariable(name: String): Boolean = name in variables || strumVariable.matches(name)

    val implemented = setOf(
        "makeLuaSprite", "makeAnimatedLuaSprite", "makeGraphic", "luaSpriteMakeGraphic", "loadGraphic", "loadFrames",
        "addAnimationByPrefix", "luaSpriteAddAnimationByPrefix", "addAnimationByIndices", "luaSpriteAddAnimationByIndices",
        "addAnimationByIndicesLoop", "addAnimation", "addOffset", "playAnim", "objectPlayAnimation", "luaSpritePlayAnimation",
        "addLuaSprite", "removeLuaSprite", "luaSpriteExists", "setScrollFactor", "setLuaSpriteScrollFactor", "scaleObject",
        "scaleLuaSprite", "setGraphicSize", "updateHitbox", "screenCenter", "setObjectCamera", "setLuaSpriteCamera",
        "setObjectOrder", "getObjectOrder", "setBlendMode", "getMidpointX", "getMidpointY", "getGraphicMidpointX",
        "getGraphicMidpointY", "getScreenPositionX", "getScreenPositionY", "objectsOverlap", "precacheImage", "precacheSound",
        "precacheMusic", "makeLuaText", "addLuaText", "removeLuaText", "luaTextExists", "setTextString", "getTextString",
        "setTextSize", "getTextSize", "setTextWidth", "getTextWidth", "setTextHeight", "setTextColor", "setTextFont", "getTextFont",
        "setTextBorder", "setTextAlignment", "setTextItalic", "setTextAutoSize", "getProperty", "setProperty", "getPropertyFromClass",
        "setPropertyFromClass", "getPropertyFromGroup", "setPropertyFromGroup", "getPropertyLuaSprite", "setPropertyLuaSprite",
        "setVar", "getVar", "callMethod", "doTweenX", "doTweenY", "doTweenAlpha", "doTweenAngle", "doTweenZoom", "doTweenColor",
        "startTween", "cancelTween", "noteTweenX", "noteTweenY", "noteTweenAlpha", "noteTweenAngle", "noteTweenDirection",
        "runTimer", "cancelTimer", "cameraShake", "cameraFlash", "cameraFade", "cameraSetTarget", "setCameraFollowPoint",
        "getCameraFollowX", "getCameraFollowY", "triggerEvent", "characterPlayAnim", "characterDance", "getCharacterX",
        "getCharacterY", "setCharacterX", "setCharacterY", "setHealth", "getHealth", "addHealth", "setScore", "addScore",
        "setMisses", "addMisses", "setHits", "addHits", "getSongPosition", "endSong", "restartSong", "exitSong", "startCountdown",
        "setHealthBarColors", "setTimeBarColors", "setRatingPercent", "setRatingName", "setRatingFC", "updateScoreText",
        "playSound", "playMusic", "stopSound", "pauseSound", "resumeSound", "setSoundVolume", "getSoundVolume", "getSoundTime",
        "setSoundTime", "getSoundPitch", "setSoundPitch", "soundFadeIn", "soundFadeOut", "soundFadeCancel", "luaSoundExists",
        "musicFadeIn", "musicFadeOut", "getRandomInt", "getRandomFloat", "getRandomBool", "getColorFromHex", "getColorFromName",
        "getColorFromString", "stringStartsWith", "stringEndsWith", "stringSplit", "stringTrim", "debugPrint", "close",
        "keyJustPressed", "keyPressed", "keyReleased", "keyboardJustPressed", "keyboardPressed", "keyboardReleased",
        "mouseClicked", "mousePressed", "mouseReleased", "getMouseX", "getMouseY", "initSaveData", "getDataFromSave",
        "setDataFromSave", "flushSaveData", "eraseSaveData", "callOnLuas", "callOnScripts", "callOnHScript", "setOnLuas",
        "setOnScripts", "setOnHScript", "callScript", "isRunning", "addCharacterToList", "checkFileExists", "getTextFromFile",
        "getModSetting", "setHealthBarColors", "setSpriteShader", "removeSpriteShader", "initLuaShader", "setShaderFloat",
        "setShaderInt", "setShaderBool", "setShaderFloatArray", "getShaderFloat", "addHaxeLibrary", "cameraZoomOnBeat",
        "getShaderInt", "getShaderBool", "getShaderFloatArray", "getShaderIntArray", "getShaderBoolArray", "setShaderIntArray",
        "setShaderBoolArray", "setShaderSampler2D", "makeFlxAnimateSprite", "loadAnimateAtlas", "addAnimationBySymbol",
        "addAnimationBySymbolIndices", "instanceArg", "addCameraFollowPoint", "setCameraScroll", "addCameraScroll",
        "getCameraScrollX", "getCameraScrollY", "gamepadAnalogX", "gamepadAnalogY", "gamepadJustPressed", "gamepadPressed",
        "gamepadReleased", "anyGamepadJustPressed", "anyGamepadPressed", "anyGamepadReleased", "getPixelColor",
        "loadMultipleFrames", "updateHitboxFromGroup", "directoryFileList", "loadSong", "startVideo", "createInstance",
        "addInstance", "callMethodFromClass", "addToGroup", "removeFromGroup"
    )

    val notImplemented = setOf(
        "openCustomSubstate", "closeCustomSubstate", "insertToCustomSubstate", "startDialogue", "addLuaScript", "removeLuaScript",
        "addHScript", "removeHScript", "saveFile", "deleteFile", "getRunningScripts", "runHaxeCode", "runHaxeFunction"
    )

    val callbacks = setOf(
        "onCreate", "onCreatePost", "onStartCountdown", "onCountdownStarted", "onCountdownTick", "onUpdate", "onUpdatePost",
        "onBeatHit", "onStepHit", "onSectionHit", "onSongStart", "onEndSong", "goodNoteHit", "goodNoteHitPre", "opponentNoteHit",
        "opponentNoteHitPre", "noteMiss", "noteMissPress", "onSpawnNote", "onEvent", "onPause", "onResume", "onGameOver",
        "onDestroy", "onMoveCamera", "onTimerCompleted", "onTweenCompleted", "onSoundFinished"
    )
}
