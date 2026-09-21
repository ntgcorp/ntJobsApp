Attribute VB_Name = "test_acJobsApp"
'==============================================================================
' test_acJobsApp.bas - Esempio d'uso della classe acJobsApp (MS Access)
' Importare in Access: acJobsApp.cls (classe) + questo modulo standard.
' Esecuzione: aprire la finestra Immediata (CTRL+G) e lanciare Test_Lifecycle.
' Niente riferimenti richiesti (late binding).
'==============================================================================
Option Explicit

Private mApp As acJobsApp ' istanza corrente, usata dalla callback per JobReturn

'--- Callback dei job: nome passato a Run(). Riceve il Dictionary del job. ---
Public Function TestCb_Job(ByVal dJob As Object) As String
    Dim sCmd As String: sCmd = ""
    If dJob.Exists("COMMAND") Then sCmd = CStr(dJob("COMMAND"))
    If sCmd = "SALUTA" Then
        Dim sNome As String: sNome = "mondo"
        If dJob.Exists("PARAM.NAME") Then sNome = CStr(dJob("PARAM.NAME"))
        TestCb_Job = mApp.JobReturn("", "Ciao " & sNome & "!")
    ElseIf sCmd = "SOMMA" Then
        On Error GoTo NumErr
        Dim a As Long: a = CLng(Trim(CStr(dJob("PARAM.A"))))
        Dim b As Long: b = CLng(Trim(CStr(dJob("PARAM.B"))))
        TestCb_Job = mApp.JobReturn("", "Somma=" & (a + b))
    Else
        TestCb_Job = mApp.JobReturn("Comando sconosciuto: " & sCmd, "")
    End If
    Exit Function
NumErr:
    TestCb_Job = mApp.JobReturn("Parametri non numerici", "")
End Function

'--- Ciclo di vita completo: crea .ini di prova, Start -> Run -> End ---
Public Sub Test_Lifecycle()
    Dim sIni As String: sIni = CurrentProject.Path & "\test_lavoro.ini"

    ' 1. Costruisci il file .ini di prova (solo PARAM.*, nessun FILE.* richiesto)
    Dim data As Object: Set data = CreateObject("Scripting.Dictionary")
    Dim cfg As Object: Set cfg = CreateObject("Scripting.Dictionary")
    cfg.Add "TYPE", "NTJOBS.APP.1.0"
    cfg.Add "NAME", "DEMO_VBA"
    cfg.Add "EXIT", "TRUE"
    cfg.Add "LOG", "test_demo.log"
    cfg.Add "BASE_DIR", "C:\dati"
    data.Add "CONFIG", cfg
    Dim j1 As Object: Set j1 = CreateObject("Scripting.Dictionary")
    j1.Add "COMMAND", "SALUTA"
    j1.Add "PARAM.NAME", "Mario"
    j1.Add "PARAM.DIR", "$BASE_DIR"
    data.Add "JOB1", j1
    Dim j2 As Object: Set j2 = CreateObject("Scripting.Dictionary")
    j2.Add "COMMAND", "SOMMA"
    j2.Add "PARAM.A", "40"
    j2.Add "PARAM.B", "2"
    data.Add "JOB2", j2

    Set mApp = New acJobsApp
    Dim w As String: w = mApp.SaveDictToIni(data, sIni)
    If w <> "" Then Debug.Print "Errore scrittura ini: " & w: Exit Sub

    ' 2. Start -> Run -> End
    Dim sRes As String: sRes = mApp.Start(sIni)
    Dim nExit As Long
    If sRes <> "" Then
        nExit = mApp.End(sRes)
    Else
        nExit = mApp.End(mApp.Run("TestCb_Job"))
    End If
    Debug.Print "Exit code: " & nExit ' 0 atteso

    ' 3. Rileggi il .end e mostra gli esiti
    Dim sEnd As String: sEnd = CurrentProject.Path & "\test_lavoro.end"
    Dim rd As Object
    Dim rErr As String: rErr = mApp.ReadIniToDict(sEnd, rd)
    If rErr = "" Then
        Debug.Print "END CONFIG RETURN.TYPE = " & CStr(rd("CONFIG")("RETURN.TYPE"))
        Debug.Print "END JOB1 RETURN.VALUE  = " & CStr(rd("JOB1")("RETURN.VALUE"))
        Debug.Print "END JOB2 RETURN.VALUE  = " & CStr(rd("JOB2")("RETURN.VALUE"))
    End If

    ' 4. ExecReturn con timeout breve su ID inesistente -> 0 chiavi (non finito)
    Dim pend As Object: Set pend = mApp.ExecReturn("id_inesistente_xyz", 1)
    Debug.Print "execReturn ID inesistente, chiavi=" & pend.Count & " (0 = non finito, atteso)"

    ' Esempio di lancio reale (decommentare con uno script esistente):
    ' Dim cfg2 As Object: Set cfg2 = CreateObject("Scripting.Dictionary")
    ' cfg2.Add "NAME", "FIGLIA"
    ' Dim jobs As Object: Set jobs = CreateObject("Scripting.Dictionary")
    ' Dim jj As Object: Set jj = CreateObject("Scripting.Dictionary")
    ' jj.Add "COMMAND", "SALUTA": jobs.Add "JOB1", jj
    ' Debug.Print "Exec: " & mApp.Exec("C:\apps\figlia.py", cfg2, jobs, "lotto1")
    ' Dim res As Object
    ' Do
    '     Set res = mApp.ExecReturn("lotto1", 30)
    '     DoEvents
    ' Loop While res.Count = 0

    ' 5. Pulizia
    On Error Resume Next
    Kill sIni
    Kill sEnd
    Kill CurrentProject.Path & "\test_demo.log"
    On Error GoTo 0
    Set mApp = Nothing
    Debug.Print "Test completato."
End Sub

'--- Prova veloce delle sole funzioni di supporto ---
Public Sub Test_Helpers()
    Dim j As New acJobsApp
    Debug.Print "Timestamp: " & j.Timestamp()
    Dim c As Object: Set c = CreateObject("Scripting.Dictionary")
    c.Add "USER", "Mario"
    Debug.Print "Expand: " & j.Expand("Ciao $USER da $SYS.OS", c)
    Debug.Print "Bool TRUE: " & j.StringBool("TRUE")
End Sub
