package com.vconvertercrew.vconverter.ui.codename

import android.app.Application
import com.vconvertercrew.vconverter.convert.CodenameToPsychCharactersConverter
import com.vconvertercrew.vconverter.convert.CodenameToPsychScriptsConverter
import com.vconvertercrew.vconverter.convert.CodenameToPsychStagesConverter
import com.vconvertercrew.vconverter.convert.CodenameToPsychWeeksConverter
import com.vconvertercrew.vconverter.convert.PsychToCodenameCharactersConverter
import com.vconvertercrew.vconverter.convert.PsychToCodenameScriptsConverter
import com.vconvertercrew.vconverter.convert.PsychToCodenameStagesConverter
import com.vconvertercrew.vconverter.convert.PsychToCodenameWeeksConverter
import com.vconvertercrew.vconverter.ui.reverse.ReverseViewModel

class CodenameToPsychCharactersViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychCharactersConverter())

class CodenameToPsychStagesViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychStagesConverter())

class CodenameToPsychWeeksViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychWeeksConverter())

class CodenameToPsychScriptsViewModel(application: Application) : ReverseViewModel(application, CodenameToPsychScriptsConverter())

class PsychToCodenameCharactersViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameCharactersConverter())

class PsychToCodenameStagesViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameStagesConverter())

class PsychToCodenameWeeksViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameWeeksConverter())

class PsychToCodenameScriptsViewModel(application: Application) : ReverseViewModel(application, PsychToCodenameScriptsConverter())
