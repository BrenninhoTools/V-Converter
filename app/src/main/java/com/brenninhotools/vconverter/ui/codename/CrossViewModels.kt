package com.brenninhotools.vconverter.ui.codename

import android.app.Application
import com.brenninhotools.vconverter.convert.CodenameToPsychCharactersConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychChartsConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychScriptsConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychStagesConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychWeeksConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameCharactersConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameScriptsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameStagesConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameWeeksConverter
import com.brenninhotools.vconverter.ui.reverse.ReverseViewModel

class CodenameToPsychChartsViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychChartsConverter())

class CodenameToPsychCharactersViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychCharactersConverter())

class CodenameToPsychStagesViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychStagesConverter())

class CodenameToPsychWeeksViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychWeeksConverter())

class CodenameToPsychScriptsViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychScriptsConverter())

class PsychToCodenameChartsViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameChartsConverter())

class PsychToCodenameCharactersViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameCharactersConverter())

class PsychToCodenameStagesViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameStagesConverter())

class PsychToCodenameWeeksViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameWeeksConverter())

class PsychToCodenameScriptsViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameScriptsConverter())
