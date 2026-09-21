Attribute VB_Name = "test_acJobsApp"
'==============================================================================
' test_acJobsApp.bas - Esempio d'uso della classe acJobsApp (MS Access)
' Importare in Access: acJobsApp.cls (classe) + questo modulo standard.
' Usa il file condiviso test_acJobsApp.ini nella stessa cartella del progetto
' (NON lo crea, NON lo cancella): Start -> Run -> End, poi pulisce .end e .log.
' Esecuzione: aprire la finestra Immediata (CTRL+G) e lanciare Test_Lifecycle.
' Niente riferimenti richiesti (late binding).
'==============================================================================
Option Explicit

Private Const MAX_OUT As Long = 2000

Private mApp As acJobsApp ' istanza corrente, usata dalla callback per JobReturn
Private mWorkDir As String

'--- Esegue un comando di shell, output in ByRef. Ritorna "" se ok. ---
Private Function RunShell(ByVal sCmd As String, ByRef sOut As String) As String
    On Error GoTo Eh
    Dim sh As Object: Set sh = CreateObject("WScript.Shell")
    sh.CurrentDirectory = mWorkDir
    Dim ex As Object: Set ex = sh.Exec(sCmd)
    Do While ex.Status = 0
        DoEvents
    Loop
    sOut = ex.StdOut.ReadAll()
    Dim sErr As String: sErr = ex.StdErr.ReadAll()
    If sErr <> "" Then sOut = sOut & sErr
    If Len(sOut) > MAX_OUT Then sOut = Left(sOut, MAX_OUT)
    If ex.ExitCode <> 0 Then RunShell = "exit=" & ex.ExitCode Else RunShell = ""
    Exit Function
Eh:
    sOut = ""
    RunShell = "Errore shell: " & Err.Description
End Function

'--- Callback dei job: nome passato a Run(). Riceve il Dictionary del job. ---
' Supporta COMMAND=SHELL (esegue PARAM.CMD) e, in alternativa, esegue
' direttamente il valore di COMMAND come comando di shell
' (es. COMMAND=cmd.exe /c dir *.* /b).
Public Function TestCb_Job(ByVal dJob As Object) As String
    Dim sCmd As String: sCmd = ""
    If dJob.Exists("COMMAND") Then sCmd = CStr(dJob("COMMAND"))
    If sCmd = "SHELL" Then
        Dim sShell As String: sShell = ""
        If dJob.Exists("PARAM.CMD") Then sShell = CStr(dJob("PARAM.CMD"))
        If sShell = "" Then TestCb_Job = mApp.JobReturn("PARAM.CMD mancante", ""): Exit Function
        Dim sOut1 As String
        Dim sErr1 As String: sErr1 = RunShell(sShell, sOut1)
        TestCb_Job = mApp.JobReturn(sErr1, sOut1)
    ElseIf sCmd <> "" Then
        Dim sOut2 As String
        Dim sErr2 As String: sErr2 = RunShell(sCmd, sOut2)
        TestCb_Job = mApp.JobReturn(sErr2, sOut2)
    Else
        TestCb_Job = mApp.JobReturn("COMMAND mancante", "")
    End If
End Function

'--- Ciclo di vita: usa test_acJobsApp.ini, Start -> Run -> End ---
Public Sub Test_Lifecycle()
    mWorkDir = CurrentProject.Path
    Dim sIni As String: sIni = mWorkDir & "\test_acJobsApp.ini"
    If Dir(sIni) = "" Then
        Debug.Print "File di prova non trovato: " & sIni
        Exit Sub
    End If

    Set mApp = New acJobsApp
    Dim sRes As String: sRes = mApp.Start(sIni)
    Dim nExit As Long
    If sRes <> "" Then
        nExit = mApp.End(sRes)
    Else
        nExit = mApp.End(mApp.Run("TestCb_Job"))
    End If
    Debug.Print "Exit code: " & nExit ' 0 atteso

    ' Rileggi il .end e mostra gli esiti
    Dim sEnd As String: sEnd = mWorkDir & "\test_acJobsApp.end"
    Dim rd As Object
    Dim rErr As String: rErr = mApp.ReadIniToDict(sEnd, rd)
    If rErr = "" Then
        Debug.Print "END CONFIG RETURN.TYPE = " & CStr(rd("CONFIG")("RETURN.TYPE"))
        Debug.Print "END JOB1 RETURN.TYPE   = " & CStr(rd("JOB1")("RETURN.TYPE"))
        Debug.Print "END JOB2 RETURN.TYPE   = " & CStr(rd("JOB2")("RETURN.TYPE"))
    End If

    ' Pulizia dei soli artefatti generati (l'ini condiviso resta)
    On Error Resume Next
    Kill sEnd
    Kill mWorkDir & "\test_acJobsApp.log"
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
