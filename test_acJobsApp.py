"""test_acJobsApp.py - Esempio d'uso della libreria acJobsApp (Python).

Uso:
    python test_acJobsApp.py [percorso_ini]

Usa il file condiviso test_acJobsApp.ini (NON lo crea, NON lo cancella):
esegue i job con Start -> Run -> End e pulisce solo .end e .log generati.
La callback supporta COMMAND=SHELL (esegue PARAM.CMD) e, in alternativa,
esegue direttamente il valore di COMMAND come comando di shell
(es. COMMAND=cmd.exe /c dir *.* /b).
"""
import os
import subprocess
import sys

from acJobsApp import acJobsApp

MAX_OUT = 2000


def run_shell(sCmd, sWorkDir):
    """Esegue un comando di shell, ritorna (errore, output)."""
    try:
        p = subprocess.run(sCmd, shell=True, capture_output=True, text=True,
                           cwd=sWorkDir, timeout=120, errors="replace")
        sOut = (p.stdout or "") + (p.stderr or "")
        if len(sOut) > MAX_OUT:
            sOut = sOut[:MAX_OUT]
        if p.returncode != 0:
            return f"exit={p.returncode}", sOut
        return "", sOut
    except Exception as e:
        return f"Errore shell: {e}", ""


def main():
    sBase = os.path.dirname(os.path.abspath(__file__))
    if len(sys.argv) < 2:
        sys.argv.append(os.path.join(sBase, "test_acJobsApp.ini"))
    sIni = sys.argv[1]
    if not os.path.isfile(sIni):
        print(f"File di prova non trovato: {sIni}")
        return

    jData = acJobsApp()

    def cbCommands(dJob):
        sCmd = dJob.get("COMMAND", "")
        if sCmd == "SHELL":
            sShell = dJob.get("PARAM.CMD", "")
            if not sShell:
                return jData.Return("PARAM.CMD mancante", "")
            sErr, sOut = run_shell(sShell, sBase)
            return jData.Return(sErr, sOut)
        if sCmd:
            sErr, sOut = run_shell(sCmd, sBase)
            return jData.Return(sErr, sOut)
        return jData.Return("COMMAND mancante", "")

    sResult = jData.Start()
    # Nota: End() chiama sys.exit() in caso di errore: intercetta SystemExit
    # cosi' la pulizia degli artefatti viene eseguita comunque.
    nCode = 0
    try:
        if sResult != "":
            nCode = jData.End(sResult)
        else:
            nCode = jData.End(jData.Run(cbCommands))
    except SystemExit as e:
        nCode = e.code if isinstance(e.code, int) else 2
    if nCode is None:
        nCode = 0  # End() ritorna None su successo (exit 0 senza sys.exit)
    print(f"Exit code: {nCode}")

    # Pulizia dei soli artefatti generati (l'ini condiviso resta)
    base, _ = os.path.splitext(sIni)
    for f in (base + ".end", os.path.join(sBase, "test_acJobsApp.log")):
        try:
            if os.path.isfile(f):
                os.remove(f)
        except OSError:
            pass
    print("Test completato.")


if __name__ == "__main__":
    main()
