VERSION 1.0 CLASS
BEGIN
  MultiUse = -1  'True
END
Attribute VB_Name = "acJobsApp"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = False
Attribute VB_Exposed = False
'==============================================================================
' acJobsApp.cls - Orchestratore per applicazioni ntJobsApp (port VBA di acJobsApp.py)
' Per MS Access. Zero riferimenti richiesti (Dictionary via late binding).
' Nota: i file .ini/.end sono letti/scritti in ANSI (istruzioni Open native).
' Uso tipico (vedi test_acJobsApp.bas):
'   Dim j As New acJobsApp
'   Dim sRes As String: sRes = j.Start("C:\dati\lavoro.ini")
'   If sRes <> "" Then j.End sRes Else j.End j.Run("TestCb_Job")
' Nomi: Return() e' parola riservata (GoSub...Return), quindi qui si chiama JobReturn().
' End() restituisce il codice di uscita (0/1/2) invece di chiudere Access.
'==============================================================================
Option Explicit

#If VBA7 Then
    Private Declare PtrSafe Sub Sleep Lib "kernel32" (ByVal dwMilliseconds As Long)
#Else
    Private Declare Sub Sleep Lib "kernel32" (ByVal dwMilliseconds As Long)
#End If

Public Const NTJOBSAPP_VER As String = "2024102412" ' YYYYMMDDHH (statico)

'--- Stato istanza ---
Private pJobIni As String
Private pUser As String
Private pUsg As String
Private pErrExit As Boolean
Private pName As String
Private pTsStart As String
Private pType As String
Private pLogFile As String
Private pCommand As String
Private pDictJob As Object      ' Scripting.Dictionary (job corrente)
Private pDictJobs As Object     ' Scripting.Dictionary di Dictionary (sezione -> job)
Private pJobEnd As String
Private pDictExec As Object     ' Scripting.Dictionary (sID -> Dictionary INI/END/SCRIPT/TS)
Private pLog As String          ' percorso file di log ("" = solo Immediate)

'==============================================================================
' Costruttore
'==============================================================================
Private Sub Class_Initialize()
    Set pDictJobs = CreateObject("Scripting.Dictionary")
    Set pDictJob = CreateObject("Scripting.Dictionary")
    Set pDictExec = CreateObject("Scripting.Dictionary")
    pUser = Environ("NTJ_USER")
    pUsg = Environ("NTJ_USERG")
    pErrExit = False
    pLog = ""
End Sub

'==============================================================================
' Proprieta' di lettura
'==============================================================================
Public Property Get sJobIni() As String: sJobIni = pJobIni: End Property
Public Property Get sJobEnd() As String: sJobEnd = pJobEnd: End Property
Public Property Get sName() As String: sName = pName: End Property
Public Property Get sCommand() As String: sCommand = pCommand: End Property
Public Property Get sUser() As String: sUser = pUser: End Property
Public Property Get asUsg() As String: asUsg = pUsg: End Property
Public Property Get DictJobs() As Object: Set DictJobs = pDictJobs: End Property
Public Property Get DictJob() As Object: Set DictJob = pDictJob: End Property

'==============================================================================
' Helper
'==============================================================================
Public Function NormalizePath(ByVal sPath As String) As String
    NormalizePath = Replace(sPath, "/", "\")
End Function

Private Function ErrorProc(ByVal sResult As String, ByVal sProc As String) As String
    If sResult <> "" Then ErrorProc = sProc & ": Errore " & sResult Else ErrorProc = ""
End Function

Public Function Timestamp(Optional ByVal sPostfix As String = "") As String
    On Error GoTo Eh
    Dim s As String: s = Format(Now, "yyyymmdd:hhnnss")
    If sPostfix <> "" Then s = s & ":" & LCase(sPostfix)
    Timestamp = s
    Exit Function
Eh:
    Timestamp = ""
End Function

Public Function TimestampValidate(ByVal sTs As String) As Boolean
    On Error GoTo No
    If Len(sTs) < 15 Then GoTo No
    Dim p() As String: p = Split(sTs, ":")
    If UBound(p) < 1 Then GoTo No
    If Len(p(0)) <> 8 Or Len(p(1)) <> 6 Then GoTo No
    Dim dt As Date
    dt = DateSerial(CInt(Left(p(0), 4)), CInt(Mid(p(0), 5, 2)), CInt(Mid(p(0), 7, 2))) + _
         TimeSerial(CInt(Left(p(1), 2)), CInt(Mid(p(1), 3, 2)), CInt(Mid(p(1), 6, 2)))
    TimestampValidate = True
    Exit Function
No:
    TimestampValidate = False
End Function

Private Function IsAlphaNum(ByVal ch As String) As Boolean
    Dim c As String: c = UCase(Left(ch, 1))
    IsAlphaNum = (c >= "A" And c <= "Z") Or (c >= "0" And c <= "9") Or c = "_"
End Function

Private Function SysVar(ByVal sVar As String) As String
    Select Case sVar
        Case "OS": SysVar = IIf(InStr(1, Environ("OS"), "Windows", vbTextCompare) > 0, "WINDOWS", UCase(Environ("OS")))
        Case "OS2": SysVar = "ACCESS" & Application.Version
        Case "USER": SysVar = Environ("USERNAME")
        Case "COMPUTER": SysVar = Environ("COMPUTERNAME")
        Case "CD": SysVar = CurrentProject.Path
        Case "TEMP": SysVar = Environ("TEMP")
        Case "YYYYMMDD": SysVar = Format(Date, "yyyymmdd")
        Case "NOW": SysVar = Timestamp()
        Case Else: SysVar = "NOTFOUND"
    End Select
End Function

'--- Expand: stesse 3 fasi della versione Python ---
Public Function Expand(ByVal sText As String, ByVal dictConfig As Object) As String
    On Error GoTo Eh
    sText = Replace(sText, "%##", "#")
    sText = Replace(sText, "%#", """")
    sText = Replace(sText, "%%", "%")
    sText = Replace(sText, "%n", vbLf)
    sText = Replace(sText, "%$", "$")
    Dim out As String: out = ""
    Dim i As Long: i = 1
    Dim n As Long: n = Len(sText)
    Do While i <= n
        Dim ch As String: ch = Mid(sText, i, 1)
        If ch = "$" Then
            Dim rest As String: rest = Mid(sText, i + 1)
            If Left(rest, 4) = "SYS." Or Left(rest, 4) = "ENV." Then
                Dim pre As String: pre = Left(rest, 4)
                Dim j As Long: j = 5
                Dim nm As String: nm = ""
                Do While j <= Len(rest) And IsAlphaNum(Mid(rest, j, 1))
                    nm = nm & Mid(rest, j, 1): j = j + 1
                Loop
                If pre = "ENV." Then
                    out = out & Environ(nm)
                Else
                    out = out & SysVar(nm)
                End If
                i = i + 4 + Len(nm)
            Else
                Dim k As Long: k = 1
                Dim nm2 As String: nm2 = ""
                Do While k <= Len(rest) And IsAlphaNum(Mid(rest, k, 1))
                    nm2 = nm2 & Mid(rest, k, 1): k = k + 1
                Loop
                If nm2 = "" Then
                    out = out & "$": i = i + 1
                ElseIf Not dictConfig Is Nothing And dictConfig.Exists(nm2) Then
                    out = out & CStr(dictConfig(nm2)): i = i + 1 + Len(nm2)
                Else
                    out = out & "$" & nm2: i = i + 1 + Len(nm2)
                End If
            End If
        Else
            out = out & ch: i = i + 1
        End If
    Loop
    Expand = out
    Exit Function
Eh:
    Expand = ErrorProc(Err.Description, "Expand")
End Function

Public Function ExpandDict(ByVal dictExpand As Object, ByVal dictParam As Object) As Object
    Dim d As Object: Set d = CreateObject("Scripting.Dictionary")
    Dim k As Variant
    For Each k In dictExpand.Keys
        d.Add k, Expand(CStr(dictExpand(k)), dictParam)
    Next k
    Set ExpandDict = d
End Function

Public Function StringBool(ByVal sText As String) As Boolean
    StringBool = (LCase(Trim(sText)) = "true")
End Function

Private Function DictClone(ByVal d As Object) As Object
    Dim c As Object: Set c = CreateObject("Scripting.Dictionary")
    Dim k As Variant
    For Each k In d.Keys: c.Add k, d(k): Next k
    Set DictClone = c
End Function

'--- INI: lettura (restituisce "" se ok, il dict in ByRef) ---
Public Function ReadIniToDict(ByVal sPath As String, ByRef outDict As Object) As String
    On Error GoTo Eh
    Set outDict = CreateObject("Scripting.Dictionary")
    Dim fh As Integer: fh = FreeFile
    Open sPath For Input Access Read As #fh
    Dim sSect As String: sSect = ""
    Dim nSect As Long: nSect = 0
    Do Until EOF(fh)
        Dim ln As String: Line Input #fh, ln
        ln = Trim(ln)
        If ln <> "" And Left(ln, 1) <> ";" Then
            If Left(ln, 1) = "[" And Right(ln, 1) = "]" And Len(ln) > 2 Then
                sSect = Mid(ln, 2, Len(ln) - 2)
                If Not outDict.Exists(sSect) Then
                    outDict.Add sSect, CreateObject("Scripting.Dictionary")
                    nSect = nSect + 1
                End If
            ElseIf sSect <> "" Then
                Dim eq As Long: eq = InStr(1, ln, "=")
                If eq > 0 Then
                    Dim kk As String: kk = Trim(Left(ln, eq - 1))
                    Dim vv As String: vv = Trim(Mid(ln, eq + 1))
                    Dim dd As Object: Set dd = outDict(sSect)
                    If dd.Exists(kk) Then dd(kk) = vv Else dd.Add kk, vv
                End If
            End If
        End If
    Loop
    Close #fh
    Debug.Print "Letto file .ini " & sPath & ", Numero Sezioni: " & nSect
    ReadIniToDict = ""
    Exit Function
Eh:
    On Error Resume Next: Close #fh
    ReadIniToDict = ErrorProc(Err.Description, "ReadIniToDict")
End Function

Public Function SaveDictToIni(ByVal dataDict As Object, ByVal sPath As String) As String
    On Error GoTo Eh
    Dim fh As Integer: fh = FreeFile
    Open sPath For Output Access Write As #fh
    Dim s As Variant, kk As Variant
    For Each s In dataDict.Keys
        Print #fh, "[" & CStr(s) & "]"
        Dim dd As Object: Set dd = dataDict(s)
        For Each kk In dd.Keys
            Print #fh, CStr(kk) & "=" & CStr(dd(kk))
        Next kk
    Next s
    Close #fh
    SaveDictToIni = ""
    Exit Function
Eh:
    On Error Resume Next: Close #fh
    SaveDictToIni = ErrorProc(Err.Description, "SaveDictToIni")
End Function

'==============================================================================
' Log interno
'==============================================================================
Private Function LogStart(ByVal sLogfile As String) As String
    On Error GoTo Eh
    Dim sFolder As String: sFolder = CurrentProject.Path
    If sLogfile = "" Then sLogfile = "acJobsApp"
    If LCase(Right(sLogfile, 4)) = ".log" Then sLogfile = Left(sLogfile, Len(sLogfile) - 4)
    pLog = sFolder & "\" & sLogfile & ".log"
    LogStart = ""
    Exit Function
Eh:
    LogStart = ErrorProc(Err.Description, "LogStart")
End Function

Public Sub Log(ByVal sType As String, Optional ByVal sValue As String = "")
    Dim sLine As String: sLine = Timestamp(sType) & ": " & sValue
    Debug.Print sLine
    If pLog = "" Then Exit Sub
    On Error Resume Next
    Dim fh As Integer: fh = FreeFile
    Open pLog For Append Access Write As #fh
    Print #fh, sLine
    Close #fh
End Sub

Public Sub Log0(ByVal sResult As String, Optional ByVal sValue As String = "")
    If sResult <> "" Then Log "ERR", sResult & ": " & sValue Else Log "INFO", sValue
End Sub

Public Sub Log1(Optional ByVal sValue As String = "")
    Log "INFO", sValue
End Sub

'==============================================================================
' Metodi principali
'==============================================================================
Public Function Config(ByVal sKey As String) As String
    On Error GoTo Eh
    Dim cfg As Object: Set cfg = pDictJobs("CONFIG")
    If cfg.Exists(sKey) Then Config = CStr(cfg(sKey)) Else Config = ""
    Exit Function
Eh:
    Config = ""
End Function

Public Sub AddTimestamp(ByVal dictTemp As Object)
    dictTemp("TS.START") = pTsStart
    dictTemp("TS.END") = Timestamp()
End Sub

'--- Start: legge e valida il file .ini ---
Public Function Start(ByVal sIniParam As String) As String
    Dim sProc As String: sProc = "Start"
    Dim sResult As String: sResult = ""
    pTsStart = Timestamp()
    pJobIni = ""
    pJobEnd = ""
    Set pDictJobs = CreateObject("Scripting.Dictionary")
    Set pDictJob = CreateObject("Scripting.Dictionary")
    pCommand = ""

    If Trim(sIniParam) = "" Then
        sResult = "NTJOBSAPP: Eseguire con parametro file .ini"
        Debug.Print sResult
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If
    pJobIni = NormalizePath(sIniParam)
    If Dir(pJobIni) = "" Then
        sResult = "File .ini non esistente " & pJobIni
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If

    Dim dot As Long: dot = InStrRev(pJobIni, ".")
    If dot > 0 Then pJobEnd = Left(pJobIni, dot - 1) & ".end" Else pJobEnd = pJobIni & ".end"

    Dim raw As Object
    sResult = ReadIniToDict(pJobIni, raw)
    If sResult <> "" Then
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If
    ' Maiuscole su sezioni e chiavi
    Dim s As Variant, kk As Variant
    For Each s In raw.Keys
        Dim up As Object: Set up = CreateObject("Scripting.Dictionary")
        Dim dd As Object: Set dd = raw(s)
        For Each kk In dd.Keys
            up.Add UCase(CStr(kk)), CStr(dd(kk))
        Next kk
        pDictJobs.Add UCase(CStr(s)), up
    Next s
    Debug.Print "Letto " & pJobIni

    ' Chiavi riservate
    For Each s In pDictJobs.Keys
        Dim d2 As Object: Set d2 = pDictJobs(s)
        For Each kk In d2.Keys
            If kk = "TS.START" Or kk = "TS.END" Or kk = "RETURN.TYPE" Or kk = "RETURN.VALUE" _
               Or Left(kk, Len("RETURN.FILE.")) = "RETURN.FILE." Then
                sResult = ErrorProc("Usate chiavi riservate " & kk, sProc)
                Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
                Start = sResult: Exit Function
            End If
        Next kk
    Next s
    Debug.Print "Processato " & pJobIni & ", Sezioni " & Join(pDictJobs.Keys, ", ")

    If Not pDictJobs.Exists("CONFIG") Then
        sResult = ErrorProc("Sezione CONFIG non trovata in file " & pJobIni, sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If

    ' Espansione CONFIG in 2 cicli
    Dim cfg As Object: Set cfg = pDictJobs("CONFIG")
    Dim pass As Integer
    For pass = 1 To 2
        Dim tmp As Object: Set tmp = DictClone(cfg)
        For Each kk In tmp.Keys
            cfg(kk) = Expand(CStr(tmp(kk)), tmp)
        Next kk
    Next pass

    ' Espansione altre sezioni
    For Each s In pDictJobs.Keys
        If s <> "CONFIG" Then
            Debug.Print "Sezione: " & s
            Set pDictJobs(s) = ExpandDict(pDictJobs(s), cfg)
        End If
    Next s

    ' Verifica FILE.* (basename nella cartella corrente, salta FILE.OUT.*)
    Dim sb As String: sb = ""
    For Each s In pDictJobs.Keys
        If s <> "CONFIG" Then
            Dim dj As Object: Set dj = pDictJobs(s)
            For Each kk In dj.Keys
                If Left(kk, 5) = "FILE." And Left(kk, 9) <> "FILE.OUT." Then
                    Dim v As String: v = Trim(CStr(dj(kk)))
                    Dim bn As String: bn = Mid(v, InStrRev(v, "\") + 1)
                    bn = Mid(bn, InStrRev(bn, "/") + 1)
                    If Dir(CurrentProject.Path & "\" & bn) = "" And Dir(bn) = "" Then
                        sb = sb & "File richiesto non presente " & bn & vbLf
                    End If
                End If
            Next kk
        End If
    Next s
    If sb <> "" Then
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sb
        Start = sb: Exit Function
    End If

    pLogFile = Config("LOG")
    pType = Config("TYPE")
    pName = Config("NAME")
    pErrExit = StringBool(Config("EXIT"))
    sResult = LogStart(pLogFile)
    If sResult <> "" Then
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If
    If cfg.Exists("PASSWORD") Then cfg.Remove "PASSWORD"
    If pName = "" Then
        sResult = ErrorProc("NAME APP non precisato", sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If
    If Left(pType, Len("NTJOBS.APP.")) <> "NTJOBS.APP." Then
        sResult = ErrorProc("Type INI non NTJOBSAPP", sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Start = sResult: Exit Function
    End If
    Log0 "", ""
    Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
    Start = sResult
End Function

'--- MakeIni: crea ntjobsapp.ini da comando + coppie chiave/valore ---
Public Function MakeIni(ByVal sCommand As String, ParamArray kv() As Variant) As String
    Dim sProc As String: sProc = "MakeIni"
    pJobIni = ""
    If (UBound(kv) - LBound(kv) + 1) Mod 2 <> 0 Then
        Dim e1 As String: e1 = ErrorProc("Errore numero parametri comando chiave=valore ecc.", sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & e1
        MakeIni = e1: Exit Function
    End If
    Dim data As Object: Set data = CreateObject("Scripting.Dictionary")
    Dim c0 As Object: Set c0 = CreateObject("Scripting.Dictionary")
    c0.Add "TYPE", "NTJOBS.APP.1"
    data.Add "CONFIG", c0
    Dim j As Object: Set j = CreateObject("Scripting.Dictionary")
    j.Add "COMMAND", sCommand
    Dim i As Long
    For i = LBound(kv) To UBound(kv) Step 2
        Dim k As String: k = UCase(Trim(Replace(CStr(kv(i)), """", "")))
        Dim v As String: v = Trim(Replace(CStr(kv(i + 1)), """", ""))
        If j.Exists(k) Then j(k) = v Else j.Add k, v
    Next i
    data.Add "JOB_01", j
    Dim sFileTemp As String: sFileTemp = CurrentProject.Path & "\ntjobsapp.ini"
    Dim sResult As String: sResult = SaveDictToIni(data, sFileTemp)
    Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
    If sResult <> "" Then MakeIni = sResult: Exit Function
    pJobIni = sFileTemp
    MakeIni = ""
End Function

'--- JobReturn: registra l'esito del job corrente (dentro la callback di Run) ---
Public Function JobReturn(ByVal sResult As String, Optional ByVal sValue As String = "", Optional ByVal dictFiles As Object = Nothing) As String
    Dim sProc As String: sProc = "Return"
    Dim sRT As String: sRT = IIf(sResult <> "", "E", "S")
    If sValue = "" And sRT = "E" Then sValue = sResult
    If Not dictFiles Is Nothing Then
        Dim f As Variant
        For Each f In dictFiles.Keys
            Dim fp As String: fp = NormalizePath(CStr(dictFiles(f)))
            Dim bn As String: bn = Mid(fp, InStrRev(fp, "\") + 1)
            If Dir(CurrentProject.Path & "\" & bn) = "" And Dir(bn) = "" Then
                Dim e2 As String: e2 = ErrorProc("Errore file non presente : " & CStr(dictFiles(f)), sProc)
                Debug.Print "Eseguita ntjobsapp." & sProc & ": " & e2
                JobReturn = e2: Exit Function
            End If
            dictFiles(f) = bn
        Next f
        For Each f In dictFiles.Keys
            If pDictJob.Exists("RETURN.FILE." & f) Then pDictJob("RETURN.FILE." & f) = CStr(dictFiles(f)) _
            Else pDictJob.Add "RETURN.FILE." & f, CStr(dictFiles(f))
        Next f
    End If
    If pDictJob.Exists("RETURN.TYPE") Then pDictJob("RETURN.TYPE") = sRT Else pDictJob.Add "RETURN.TYPE", sRT
    If pDictJob.Exists("RETURN.VALUE") Then pDictJob("RETURN.VALUE") = sValue Else pDictJob.Add "RETURN.VALUE", sValue
    AddTimestamp pDictJob
    Dim out As String: out = ErrorProc(sResult, sProc)
    Debug.Print "Eseguita ntjobsapp." & sProc & ": " & out
    JobReturn = out
End Function

'--- Run: esegue ogni sezione chiamando la callback (nome funzione pubblica) ---
Public Function Run(ByVal sCallback As String) As String
    Dim sResult As String: sResult = ""
    Dim s As Variant
    For Each s In pDictJobs.Keys
        If s <> "CONFIG" Then
            Debug.Print "Esecuzione Command " & s
            Set pDictJob = DictClone(pDictJobs(s))
            If pDictJob.Exists("COMMAND") Then pCommand = CStr(pDictJob("COMMAND")) Else pCommand = ""
            If pCommand = "" Then
                sResult = "COMMAND non trovato in " & s
                Log sResult, ""
            Else
                Log1 "Eseguo il comando: " & pCommand & ", Sezione: " & s & ", TS: " & Timestamp() & ", Risultato: " & sResult
                sResult = CStr(Application.Run(sCallback, pDictJob))
                Set pDictJobs(s) = DictClone(pDictJob)
                Log1 "Eseguito il comando: " & pCommand & ", Sezione: " & s & ", TS: " & Timestamp() & ", Risultato: " & sResult
            End If
            If sResult <> "" And pErrExit Then Exit For
        End If
    Next s
    Run = sResult
End Function

'--- End: scrive il file .end, ritorna exit code 0/1/2 ---
Public Function End(ByVal sResult As String) As Long
    Dim sProc As String: sProc = "End"
    Dim bFatal As Boolean: bFatal = False
    Dim nResult As Long: nResult = 0
    If pDictJobs.Count = 0 Then
        nResult = 1
        Set pDictJobs = CreateObject("Scripting.Dictionary")
        pDictJobs.Add "CONFIG", CreateObject("Scripting.Dictionary")
        bFatal = True
    End If
    If sResult <> "" Then nResult = 2: bFatal = True
    Dim t As Object: Set t = CreateObject("Scripting.Dictionary")
    t.Add "RETURN.TYPE", ""
    t.Add "RETURN.VALUE", ""
    If bFatal Then t("RETURN.TYPE") = "E": t("RETURN.VALUE") = sResult
    AddTimestamp t
    Dim cfg As Object: Set cfg = pDictJobs("CONFIG")
    Dim k As Variant
    For Each k In t.Keys
        If cfg.Exists(k) Then cfg(k) = t(k) Else cfg.Add k, t(k)
    Next k
    Dim w As String: w = SaveDictToIni(pDictJobs, pJobEnd)
    If w = "" Then Debug.Print "Creato file " & pJobEnd
    Log sResult, "Fine applicazione " & pName
    Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
    End = nResult
End Function

'--- Exec: lancia una ntjobsapp esterna in background ---
' sScript: .py (via python) o .jar (via java -jar) o eseguibile diretto.
' dictJobs: chiave=sezione, valore=Dictionary job oppure Collection/Array di Dictionary (-> SEZ_01, _02...).
Public Function Exec(ByVal sScript As String, Optional ByVal dictConfig As Object = Nothing, Optional ByVal dictJobs As Object = Nothing, Optional ByVal sID As String = "") As String
    Dim sProc As String: sProc = "Exec"
    Dim sResult As String: sResult = ""
    On Error GoTo Eh
    Dim okID As Boolean: okID = (Len(sID) > 0)
    Dim i As Long
    For i = 1 To Len(sID)
        Dim ch As String: ch = Mid(sID, i, 1)
        If Not (ch Like "[A-Za-z0-9]" Or ch = "_" Or ch = "-") Then okID = False: Exit For
    Next i
    If Not okID Then
        sResult = ErrorProc("sID non valido " & sID, sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Exec = sResult: Exit Function
    End If
    If Trim(sScript) = "" Then
        sResult = ErrorProc("Script da eseguire non precisato", sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Exec = sResult: Exit Function
    End If
    Dim sNorm As String: sNorm = NormalizePath(sScript)
    If Dir(sNorm) = "" Then
        sResult = ErrorProc("Script non esistente " & sNorm, sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Exec = sResult: Exit Function
    End If
    Dim sFolder As String: sFolder = Left(sNorm, InStrRev(sNorm, "\") - 1)
    If sFolder = "" Then sFolder = CurrentProject.Path
    Dim sIni As String: sIni = sFolder & "\ntjobsapp_" & sID & ".ini"
    Dim sEndF As String: sEndF = sFolder & "\ntjobsapp_" & sID & ".end"

    Dim data As Object: Set data = CreateObject("Scripting.Dictionary")
    Dim cfg As Object: Set cfg = CreateObject("Scripting.Dictionary")
    cfg.Add "TYPE", "NTJOBS.APP.1"
    If Not dictConfig Is Nothing Then
        Dim kc As Variant
        For Each kc In dictConfig.Keys
            Dim uk As String: uk = UCase(CStr(kc))
            If cfg.Exists(uk) Then cfg(uk) = CStr(dictConfig(kc)) Else cfg.Add uk, CStr(dictConfig(kc))
        Next kc
    End If
    data.Add "CONFIG", cfg
    If Not dictJobs Is Nothing Then
        Dim s As Variant
        For Each s In dictJobs.Keys
            Dim sSec As String: sSec = UCase(CStr(s))
            If TypeName(dictJobs(s)) = "Dictionary" Then
                data.Add sSec, UpperDict(dictJobs(s))
            Else
                Dim idx As Integer: idx = 1
                Dim it As Variant
                For Each it In dictJobs(s)
                    data.Add sSec & "_" & Format(idx, "00"), UpperDict(it)
                    idx = idx + 1
                Next it
            End If
        Next s
    End If
    sResult = SaveDictToIni(data, sIni)
    If sResult <> "" Then
        sResult = ErrorProc(sResult, sProc)
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & sResult
        Exec = sResult: Exit Function
    End If
    On Error Resume Next: Kill sEndF: On Error GoTo Eh
    Dim cmd As String
    If LCase(Right(sNorm, 3)) = ".py" Then
        cmd = "python """ & sNorm & """ """ & sIni & """"
    ElseIf LCase(Right(sNorm, 4)) = ".jar" Then
        cmd = "java -jar """ & sNorm & """ """ & sIni & """"
    Else
        cmd = """" & sNorm & """ """ & sIni & """"
    End If
    On Error GoTo Eh
    Shell cmd, vbHide
    Dim info As Object: Set info = CreateObject("Scripting.Dictionary")
    info.Add "INI", sIni: info.Add "END", sEndF: info.Add "SCRIPT", sNorm: info.Add "TS", Timestamp()
    If pDictExec.Exists(sID) Then pDictExec.Remove sID
    pDictExec.Add sID, info
    Log1 "Eseguita ntjobsapp esterna: " & sNorm & ", ID: " & sID & ", File: " & sIni
    Debug.Print "Eseguita ntjobsapp." & sProc & ": "
    Exec = ""
    Exit Function
Eh:
    Exec = ErrorProc(Err.Description, sProc)
End Function

Private Function UpperDict(ByVal d As Object) As Object
    Dim u As Object: Set u = CreateObject("Scripting.Dictionary")
    Dim k As Variant
    For Each k In d.Keys
        u.Add UCase(CStr(k)), CStr(d(k))
    Next k
    Set UpperDict = u
End Function

'--- ExecReturn: attende ntjobsapp_[sID].end. Vuoto (0 chiavi) = non finito. ---
Public Function ExecReturn(ByVal sID As String, Optional ByVal nTimeout As Long = 30) As Object
    Dim sProc As String: sProc = "ExecReturn"
    Dim out As Object: Set out = CreateObject("Scripting.Dictionary")
    On Error GoTo Eh
    If Len(sID) = 0 Then
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & ErrorProc("sID non valido " & sID, sProc)
        out.Add "ERROR", "sID non valido " & sID
        Set ExecReturn = out: Exit Function
    End If
    Dim sEndF As String, sIni As String
    If pDictExec.Exists(sID) Then
        sEndF = CStr(pDictExec(sID)("END")): sIni = CStr(pDictExec(sID)("INI"))
    Else
        sEndF = CurrentProject.Path & "\ntjobsapp_" & sID & ".end"
        sIni = CurrentProject.Path & "\ntjobsapp_" & sID & ".ini"
    End If
    Dim t0 As Single: t0 = Timer
    Do While Dir(sEndF) = ""
        If Timer - t0 >= nTimeout Then
            Debug.Print "Eseguita ntjobsapp." & sProc & ": "
            Set ExecReturn = out: Exit Function ' vuoto = non finito
        End If
        Sleep 500: DoEvents
    Loop
    Sleep 200
    Dim rd As Object
    Dim rErr As String: rErr = ReadIniToDict(sEndF, rd)
    If rErr <> "" Then
        Debug.Print "Eseguita ntjobsapp." & sProc & ": " & ErrorProc(rErr, sProc)
        out.Add "ERROR", rErr
        Set ExecReturn = out: Exit Function
    End If
    On Error Resume Next: Kill sEndF: Kill sIni: On Error GoTo Eh
    If pDictExec.Exists(sID) Then pDictExec.Remove sID
    Log1 "Letto risultato ntjobsapp esterna ID: " & sID & ", Sezioni: " & rd.Count
    Debug.Print "Eseguita ntjobsapp." & sProc & ": "
    Set ExecReturn = rd
    Exit Function
Eh:
    Set out = CreateObject("Scripting.Dictionary")
    out.Add "ERROR", Err.Description
    Set ExecReturn = out
End Function
