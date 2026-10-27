package com.brenninhotools.vconverter.ui.codename

import android.app.Application
import com.brenninhotools.vconverter.convert.CodenameCharactersConverter
import com.brenninhotools.vconverter.convert.CodenameScriptsConverter
import com.brenninhotools.vconverter.convert.CodenameStagesConverter
import com.brenninhotools.vconverter.convert.CodenameWeeksConverter
import com.brenninhotools.vconverter.ui.reverse.ReverseViewModel

class CodenameCharactersViewModel(application: Application) : ReverseViewModel(application, CodenameCharactersConverter())

class CodenameStagesViewModel(application: Application) : ReverseViewModel(application, CodenameStagesConverter())

class CodenameWeeksViewModel(application: Application) : ReverseViewModel(application, CodenameWeeksConverter())

class CodenameScriptsViewModel(application: Application) : ReverseViewModel(application, CodenameScriptsConverter())
