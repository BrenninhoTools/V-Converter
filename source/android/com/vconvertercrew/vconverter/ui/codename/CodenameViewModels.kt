package com.vconvertercrew.vconverter.ui.codename

import android.app.Application
import com.vconvertercrew.vconverter.convert.CodenameCharactersConverter
import com.vconvertercrew.vconverter.convert.CodenameScriptsConverter
import com.vconvertercrew.vconverter.convert.CodenameStagesConverter
import com.vconvertercrew.vconverter.convert.CodenameWeeksConverter
import com.vconvertercrew.vconverter.ui.reverse.ReverseViewModel

class CodenameCharactersViewModel(application: Application) : ReverseViewModel(application, CodenameCharactersConverter())

class CodenameStagesViewModel(application: Application) : ReverseViewModel(application, CodenameStagesConverter())

class CodenameWeeksViewModel(application: Application) : ReverseViewModel(application, CodenameWeeksConverter())

class CodenameScriptsViewModel(application: Application) : ReverseViewModel(application, CodenameScriptsConverter())
