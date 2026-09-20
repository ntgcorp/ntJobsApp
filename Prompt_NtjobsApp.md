**La classe** **acJobsApp** **per le** **ntJobApp**
Introduzione e Scopo
Una ntJobApp è una applicazione che funziona in modalità batch. In ingresso ha come parametro e legge un file .ini e restituisce un file .ini di risultato con stesso nome del file .ini parametro ma estensione .end.

ntJobsApp=applicazione che usa la classe acJobsApp.
Queste applicazioni utilizzano l’istanza della classe acJobsApp con la variabile jData.
La classe serve appunto a orchestrare queste micro applicazioni batch e controllarle mediante un file .ini passato come parametro.

Una ntJobApp ha un funzionamento semplicissimo:
- leggere un file .ini passato come parametro
- eseguire uno o più comandi richiesti dal chiamante tramite un file .ini, tramite un dizionario di parametri in ingresso e per ritorno uno status di esecuzione (stringa facoltativo, dove “”=nessun errore), un valore di risultato (stringa e facoltativo) e facoltativi uno o più file di ritorno al chiamante tramite setting aggiunti al dizionario passato come parametro.
- Restituire un file  .ini di  risultato delle elaborazioni ed un codice di risultato che può essere:
  - 0: Tutto a posto
  - 1: Non è riuscito ad arrivare all’esecuzione dei job leggendo il file ntjobs.ini
  - 2: Uno o più job è terminato con un errore.
Attributi della classe
- sJobIni: il file dei parametri .ini che viene letto alla partenza
- sUser: user. Preso da variabile d’ambiente NTJ_USER se esiste
- asUsg: Preso da variabile d’ambiente NTJ_USERG se esiste, ma convertito in array usando come separatore “,” e togliendo gli spazi
- bErrExit=Esce in caso di errore di un job, altrimenti continua con il prossimo job
- sName=Nome dell’applicazione
- tsStart=Timestamp inizio applicazione inizializzata con il metodo Start()
- sType=Tipo e versione applicazione
- sLogFile=Nome facoltativo del file di Log. Con facoltativo si intende che se non viene passato un file di log con LOG nella sezione [CONFIG] del file .ini passato come parametro, lo calcola in Start() la classe, in base al nome del programma python ma con estensione .log
- sCommand: ID dell’comando corrente
- dictJob: Dizionario del comando corrente, estratto dal metodo Run(cbCommands). Contiene anche i risultati dell’elaborazione corrente e prenderà il posto del dizionario estratto per il job corrente.
- dictJobs: Un dizionario che contiene altri dizionari.
  - Al suo interno il dizionario “CONFIG” contiene i settings passati all’applicazione
- sJobEnd: Nome del file job.end costruito partendo da sJobIni
- jLog: istanza di acLog inglobata in acJobsApp da utilizzare per il log
Veniamo ora alla logica di funzionamento e ai metodi della classe.
Librerie Accessorie
- Ti passo la libreria libreria aiSys.py ma copia SOLO le funzioni utilizzate da acJobsApp e sostituisci le chiamate alle funzioni aiSys.* a chiamate alle funzioni inglobale in acJobsApp anche quelle richiamate internamente dalle funzioni aiSys copiate. Lo scopo è fare un file unico .py acJobsApp senza chiamate a aiSys.*
Logica di funzionamento
. Crea istanza di acJobsApp in variabile globale jData.
. Esegui sResult=jData.Start().
. Si occupa di leggere il file .ini passato come parametro (o crearlo) in jData.dictJobs
. Se sResult diverso da “”:
. Esegui il metodo self.End(sResult)
. Esci dall’applicazione
. Esegui sResult=jData.Run().
. Si occupa di eseguire in sequenza ogni singolo job del jobs di self.dictJobs.
. Esegui il metodo self.End(sResult)
. Esci dall’applicazione

Nota: self.Log0(sResult) accetta anche solo un parametro, il secondo defalt=””
Classe acJobsApp
Convenzioni
- Alla fine di ogni metodo della classe acJobsApp, Start, MakeIni, Return, End:scrivi in console (usando 
print()),
- Solo per i metodi dove presente la variabile interna sResult.
- Scrivendo questa frase:“Eseguita 
ntjobsapp.“ + sProc + “: “ + sResult
- sProc viene inizializzata all’inizio di ogni funzione con il nome della funzione.
- In caso di uscita con errore o dove sResult diverso da “”, scrivilo anche in console per tutte le funzioni.
- All’inizio del programma inizializza la costante NTJOBSAPP_VER=Stringa composta da data del giorno e ora in questa forma YYYYMMDDHH, ma non calcolata, come data del giorno di generazione della classe 
acJobsApp
- Il wrapper di lettura in `ntjobsos.py` e `acJobsApp.py` deve applicare `.upper``()` a sezioni e chiavi dopo la chiamata a `aiSys.read_ini_to_dict`, oppure post-processare il dizionario restituito.

| `Generazione codice Python ` | ⚠️ REGOLA ASSOLUTA DI COMPLETEZZA SINTATTICA (OBBLIGATORIA)<br/>VIETATO troncare qualsiasi riga di codice, nome di variabile, funzione o istruzione.<br/>È SEVERAMENTE PROIBITO terminare righe con un underscore "_" isolato o seguito da a capo (es. `for sec in ini_`, `if cfg_`, `def load_`). Questi sono artefatti di generazione e rendono il file inutilizzabile.<br/>Ogni ciclo `for`, `if`, `try/except`, `def` e `class` deve essere COMPLETAMENTE CHIUSO con sintassi Python valida e indentazione corretta.<br/>Specificamente, quando itero su dizionari INI/CSV, scrivi SEMPRE il nome completo della variabile (es. `for sec in ini_data:`, `for k, v in record.items():`, `if "CONFIG" in job_data:`). MAI versioni troncate o abbreviate.<br/>Se il codice generato supera i limiti di token, interrompi l'output e scrivi SOLO "[CONTINUA?]", MA NON generare righe incomplete o sintatticamente errate.<br/>Prima di emettere il blocco, verifica che ogni due punti `:`, parentesi e indentazione sia chiusa correttamente. Nessun `SyntaxError` è accettabile. |
| --- | --- |
| `SYSROOT` | Variabile di bootstrap usata solo per trovare i file di config. Non è un fallback operativo. Inizializzala con la cartella dove si trova lo script python eseguito. |

COMPLETEZZA DEL CODICE:
   - Non troncare MAI cicli `for`, blocchi `if`, `try/except` o chiusure di funzione.
   - Verifica riga per riga che ogni blocco abbia indentazione valida e chiusura corretta prima di stampare il file.
   - Se un ciclo itera su un dizionario (es. `for sec in ini_:`), scrivilo COMPLETO: `for sec in ini_data:` con body chiuso correttamente.

Non mi cambiare il maiuscolo/minuscolo delle funzioni che ti indico io.  
E’ UNA REGOLA RIGOROSA OBBLIGATORIA.
Ad esempio Start() deve rimanere Start() come nome ed altre uguali, Run(),  Config() ecc..

REGOLA OBBLIGATORIA DI NORMALIZZAZIONE SEPARATORI PATH
Prima di QUALSIASI operazione di I/O su file o cartelle (open, os.listdir, shutil.*, configparser.*, json.dump, write_ini, read_ini, ecc.), TUTTI i percorsi stringa devono essere passati attraverso una funzione di conversione esplicita.
Implementa NormalizePath(sPath: str) -> str con questa logica:
1. Se sys.platform == "win32": sostituisci tutti i "/" con "\"
2. Se sys.platform in ("darwin", "linux"): sostituisci tutti i "\" con "/"
3. Ritorna il percorso convertito.
Applica NormalizePath() automaticamente a:
• Tutti i valori letti da ntjobs_config.ini, ntjobs_users.csv, ntjobs_actions.csv e jobs.ini
• Tutti i path costruiti con os.path.join o concatenazioni stringa
• Tutti i parametri passati a funzioni del filesystem
Questo garantisce che i separatori siano sempre coerenti con il SO in esecuzione. Nessun path
* Utilizza NormalizePath() SOLO su stringhe finali prima di chiamate al filesystem. Mantieni os.path.join() per la costruzione dei path."
* Possibilmente in ntjobsos.py i path usano il carattere “/” e poi convertito in “\” se siamo in ambiente Windows
Esempio di file ntjobsapp.ini che viene letto
[CONFIG]
TYPE=NTJOBS.APP.1.0
EXIT=TRUE
LOG=FILE.LOG
NAME=ID_APPLICAZIONE

[JOB1_ID]
COMMAND=NOME_AZIONE
FILE.ID1=PathFile1
FILE.ID2=PathFile1
PARAM.ID1=Valore
PARAM.ID2=Valore
RETURN.TYPE=
;Dove E=Errore/W=Working/Nulla=OK
RETURN.VALUE=Messaggio di ritorno facoltativo
TS.START=AAAAMMGG:HHMMSS
TS.END=AAAAMMGG:HHMMSS
RETURN.FILE.01=FILE1.TXT
RETURN.FILE.02=FILE2.TXT
Metodi della classe acJobsApp
Il file da generare è acJobsApp.py
Metodo __init__
Per inizializzare gli attribuiti(campi) della classe.
Inizializza tutti i campi ai valori di default: sJobIni="", sUser da env NTJ_USER, asUsg da env NTJ_USERG split ",", bErrExit=False, sName="", tsStart="", sType="", sLogFile="", sCommand="", dictJob={}, dictJobs={}, sJobEnd="".
Inizializza self.jLog come istanza di acLog inglobata (ex aiSys.acLog).
Metodo Start
Verrà richiamata subito dopo l’inizializzazione dell’istanza a cura dell’utente.

Utilizza la variabile sResult per memorizzare come stringa lo stato delle elaborazioni che ritorna alla fine.

Si occupa di leggere, se è il primo parametro o di creare il file .ini dai parametri, salvarlo in self.dictJobs, come pure inizializzare self.sJobIni e self.sJobEnd. All’inizio resetta sJobIni="", sJobEnd="", dictJobs={}, dictJob={}, sCommand="".
Inizializzazioni di Start()
- sResult=””
- self.tsStart=Timestamp() interno (ex aiSys.Timestamp)
- self.sUser e self.asUsg sono già inizializzati in __init__ da variabili d’ambiente (non re-inizializzarli qui)

Effettua queste elaborazioni:
- Se non vengono passati parametri
  - sResult = “NTJOBSAPP: Eseguire con parametro file .ini o nella forma ntjobsapp.py command parametro valore ecc.”,
  - Scrivilo in console
  - Esci dalla funzione ritornando sResult.
- Se ci sono parametri ma il primo parametro non termina per “.ini”  o non termina per “.INI”
`if` `not` sys`.`argv`[``1``].`lower`(``).`endswith`(``'.ini'``):`
    sResult `=` self`.`MakeIni`()`
    `if` sResult `!``=` `""``:`
        `return` sResult

`E appunto se ``self.MakeIni``() fallisce, esci dalla funzione ritornando ``sResult`
- Altrimenti
  - Salva nella variabile self.sJobIni il primo parametro dell’applicazione.
- Continuiamo:
  - Il file self.sJobIni deve esistere (è giusto che la verifica venga fatta dopo il passaggio per self.MakeIni se necessario),
    - altrimenti sResult=”File .ini non esistente “ + self.sJobIni ed esce
  - Inizializza self.sJobEnd =  usa il campo self.sJobIni, cambiando l’estensione in “.end”
  - Legge il file self.sJobIni con sResult,dictJobs=read_ini_to_dict interno (ex aiSys.read_ini_to_dict)(self.sJobIni)
  - Se sResult=””:
    - Scrivi in console: “Letto “ + self.sJobIni
    - Per tutte le chiavi delle sezioni di self.dictJobs:
- Questi settings non possono esere usati e sono riservati.
  - TS.START,
  - TS.END,
  - RETURN.TYPE,
  - RETURN.VALUE”
  - Tutti quelli che cominciano per “RETURN.FILE.”
In caso di problemi riscontrati:
sResult=”Usate chiavi riservate “ + Chiave_Riservata_Trovata
Se sResult=””:
- Scrivi in console: “Processato “ + self.sJobIni + “, Sezioni “ + lista delle keys di self.dictJobs divise da “, “

Continuamo (se sResult=””):
- Se non presente nel dizionario letto in self.dictJobs, la chiave “CONFIG”,
  - sResult= “Sezione CONFIG non trovata in file ” + self.sJobIni.
ESPANSIONE MULTI-PASS DELLA SEZIONE CONFIG:
Prima di qualunque verifica sui valori dei job, esegui obbligatoriamente 2 cicli di espansione sulla sezione self.dictJobs["CONFIG"]:
1. Crea una copia temporanea della sezione CONFIG (config_temp = self.dictJobs["CONFIG"].copy()).
2. Itera su tutte le chiavi di config_temp: se il valore è una stringa, applica self.dictJobs["CONFIG"][k] = aiSys.Expand(v, config_temp).
3. Ripeti l'intero ciclo una seconda volta sulla stessa sezione.
Questo garantisce la risoluzione completa di variabili a cascata fino a 2 livelli.
Solo dopo i 2 cicli, procedi all'espansione delle altre sezioni con: self.dictJobs[sKey] = aiSys.ExpandDict(dictTemp, self.dictJobs["CONFIG"])
- Se sResult=””:
  - Per ogni chiave (sKey) di self. dictJobs
    - Scrivi: “Sezione: “ + sKey
    - Esegui self.dictJobs[sKey]=copia di aiSys.ExpandDict(self.dictJobs[sKey], self.dictJobs[“CONFIG”])
    - Esegui aiSys.DictPrint(self.dictJobs[sKey])
- Se sResult=””, per ogni chiave del dizionario self.dictJobs, eccetto “CONFIG” (i valori sono già espansi al punto precedente, quindi le $VARIABILI definite in CONFIG risultano già risolte):
  - Estrai il dizionario corrispondente alla chiave come copia in dictTemp
  - Se in dictTemp, ci sono uno o più chiave che cominciano per “FILE.”:
    - Per ogni chiave che inizia per “FILE.” ma NON per “FILE.OUT.” (i FILE.OUT.* sono file di output e non devono pre-esistere):
      - Estrai in sFile il nome del file
      - Ripulisci sFile da eventuali path precedenti, sFile non deve avere nomi di path precedenti
      - Verifica che il file sFile esiste.
      - Se non si verifica la condizione di esistenza del file sFile nella cartella corrente:
        - accoda sResult la stringa  “File richiesto non presente ” + sFile + Invio,
        - Ma non uscire, aspetta la fine del ciclo di verifica di tutte le chiavi che iniziano per “FILE.”
- Se sResult=””:
  - sTemp= self.Config(“LOG”) Se sTemp diverso da “”: self.sLogFile=sTemp, altrimenti Inizializza self.sLogFile come specificato all’inizio.
  - self.sType=self.Config(“TYPE”)
  - self.sName=self.Config(“NAME”)
  - self.bErrExit=aiSys.StringBool(self.Config(“EXIT”)).
  - Esegue sResult=self.jLog.Start(self.sLogFile)
  - `if`` ``"PASSWORD"`` ``in`` ``self``.``dictJobs``.``get``(``"CONFIG``"``,`` ``{}``):`
    - `del`` ``self``.``dictJobs``[``"CONFIG``"``][``"PASSWORD"``]`
Verifiche sui parametri config
- Se sResult=””:
  - Se self.sName=””, sResult=”NAME APP non precisato”
  - Se self.sType non inizia con “NTJOBS.APP.”, sResult=”Type INI non NTJOBSAPP”
Ritorna sResult
Conclusione di Start
Nota: self.Log0 accetta anche un parametro solo, eventualmente il secondo default=”” se non passato
Esegui self.Log0(sResult)
Esci da self.Start, ritornando sResult.
Variabili ed espansione ($NOME) — capitolo dedicato
Come funzionano le $variabili nei file .ini delle ntJobsApp (funzioni aiSys.Expand e aiSys.ExpandDict, inglobate in acJobsApp.py, usate da Start() come descritto sopra).
Dove si definiscono e dove si usano
- Le variabili si definiscono come chiavi della sezione [CONFIG]: YA54M0=K:\_Statistiche\YA54M0.XLSX.
- Si richiamano con $NOME nei valori di qualsiasi sezione job: FILE.IN.SHEET=$YA54M0.
- Sezioni e chiavi dell'INI sono convertite in MAIUSCOLO in lettura: $ya54m0, $Ya54m0 e $YA54M0 sono equivalenti. I valori restano intatti.
Ordine vincolante in Start()
1. Lettura INI, chiavi in maiuscolo, controllo chiavi riservate, controllo presenza sezione CONFIG.
2. Espansione CONFIG in 2 cicli (multi-pass): catene tra variabili di CONFIG fino a profondità 2.
3. Espansione di ogni altra sezione con i valori di CONFIG (un solo livello, ExpandDict).
4. SOLO DOPO: verifica di esistenza dei FILE.* (salta FILE.OUT.*, che sono output) e poi LOG/NAME/TYPE/EXIT, log, controlli finali.
- REGOLA OBBLIGATORIA (fix 2026-09-20): la verifica FILE.* gira sui valori già espansi. Prima del fix girava sui valori grezzi e qualsiasi FILE.*=$VARIABILE falliva con "File richiesto non presente $VARIABILE". Resta la regola del basename: di un percorso assoluto si verifica il solo nome file nella cartella di lancio (CWD).
Le 3 fasi di aiSys.Expand (nell'ordine, non cambiare)
1. Escape %: %##→# per primo, poi %#→", %%→%, %n→a capo, %$→$. Per scrivere un $ letterale usare %$.
2. $SYS. e $ENV.: $ENV.NOME = variabile d'ambiente (stringa vuota se manca); $SYS. = OS, OS2, USER, COMPUTER, CD, TEMP, YYYYMMDD, NOW (nome sconosciuto → NOTFOUND).
3. $NOMEVAR da CONFIG: se il nome non esiste in CONFIG resta letterale (nessun errore) — occhio nei FILE.*, dove diventa "file non presente".
Limiti da ricordare
- Solo CONFIG alimenta $NOMEVAR: riferimenti a chiavi della STESSA sezione job non vengono espansi (ExpandDict usa solo CONFIG come dizionario di espansione).
- Le sezioni job hanno un solo passaggio di espansione: una $ che resta tale dopo CONFIG resta letterale.
- Esempio completo: [CONFIG] ... YA54M0=K:\_Statistiche\YA54M0.XLSX — [JOB_CMLTV] ... FILE.IN.SHEET=$YA54M0 ... → dopo Start(), FILE.IN.SHEET vale K:\_Statistiche\YA54M0.XLSX e la verifica cerca YA54M0.XLSX nella cartella di lancio.
Metodo MakeIni
E’ una modalità di sviluppo/standalone.
La variabile sResult è una stringa che memorizza lo stato della funzione la ritorna alla fine dell’esecuzione
Inizializza self.sJobIni=””
sProc=NomeFunzione
Crea un dizionario temporaneo dictTemp
Prende tutti i parametri dell’applicazione come descritto sopra e li gestisci in questo modo:
- Il numero di parametri deve essere 1 + un multiplo di 2, quindi 1 o 3 o 5 o 7, ecc.
- Quindi nella sequenza di coppia 1 con 2, 3 con 4, 5 con 6, eccSe non è così,
  - sResult=”Errore numero parametri comando chiave=valore ecc.” ed esce.
- Ogni parametro può essere racchiuso tra “”, e nel caso “” uguali considerali come uno e non il delimitatore della stringa, togliendoli poi alla fine prima di memorizzare la stringa.
- Con il primo parametro inizializza la variabile locale sCommand.
- Dal secondo parametro a coppie di 2, il primo dei due è la chiave, il secondo è il valore e accodali al dizionario dictTemp.

Continuiamo:
- Crea un file (nome sFileTemp) di nome “ntjobsapp.ini” in formato .ini nella stessa cartella dell’applicazione eseguita con queste sezioni:
  - “CONFIG”; Solo con l’entry “TYPE=NTJOBS.APP.1”
  - “JOB_01”: Con la prima entry COMMAND=sCommand
- Dalla seconda entry in poi, salva tutte le chiavi e i valori del dizionario dictTemp
- Se non riesci a creare il file sResult=Descrizione dell’errore come stringa
Se non ci sono problemi:
- self.sJobIni = sFileTemp

Ritorna ErrorProc interno (ex aiSys.ErrorProc)(sResult,sProc)
Metodo Config
Parametro: sKey
Esegui:
  return DictExist interno (ex aiSys.DictExist)(self.dictJobs[“CONFIG”], sKey, "")
Metodo AddTimestamp(dictTemp)
Il parametro dictTemp è un dizionario per rifermento.
A questo parametro aggiunge o sostituisce:
- “TS.START”: Assegnagli self.tsStart
- “TS.END”: Assegnagli Timestamp() interno (ex aiSys.Timestamp)
Metodo Return
Parametri:
- sResult: Stringa (Obbligatorio)
- sValue: Stringa. (Facoltativo)
- dictFiles: Dizionario (Facoltativo)
Viene chiamato dalla funzione globale cbCommands per aggiornare il risultato dell’elaborazione corrente dell’istanza della classe acJobsApp .
In particolare nel campo self.dictJob:
se sResult diverso da “”, sReturnType=”E” altrimenti sReturnType=”S”
Se sValue=”” e sReturnType=”E”, allora sValue=sResult
dictFiles è un parametro facoltativo. Se presente:
- La chiave è l’id del file, il valore è il nome del file.
- Fai un ciclo sostituendo a tutti i valori il nome del file senza path, solo nome del file ed estensione.
- Verifica che tutti i file esistono “nella cartella corrente”, se non è così, sResult diventa “Errore file non presente: ” + sFile ed esci dal ciclo di verifica.
- Se dictFiles è stato passato come parametro, accoda a self.dictJob, tutte le chiavi di dictFiles ma “precedute” dal prefisso “RETURN.FILE.” e come valore il valore di ogni chiave di self.dictFiles
- Se sReturnType=””, sReturnType=”S”

Alla fine in qualunque caso:
- Aggiungi a self.dictJob: “RETURN.TYPE”=sReturnType
- Aggiungi a self.dictJob: “RETURN.VALUE”=sValue
- Richiama self.AddTimestamp(self.dictJob)

Esci ritornando ErrorProc interno (ex aiSys.ErrorProc)(sResult,sProc)
Metodo Run
cbCommands parametro è il nome di una funzione da richiamare così:
- sResult=cbCommands(self.dictJob).

Esegue un ciclo con queste attività.
I cicli dipendono dal numero di chiavi contenute in self.dictJobs
- Scrivi in console: “Esecuzione Command “ + sKey
- Memorizza la chiave corrente in sKey. Se sKey=“CONFIG”, non fare nulla in questo ciclo.
- Altrimenti:
  - sKey=chiave corrente del ciclo
  - sResult=””
  - Copia in self.dictJob il valore corrispondente a sKey
  - self.sCommand=DictExist interno (ex aiSys.DictExist)(self.dictJob, “COMMAND”, “”)
  - se self.sCommand=””:
    - sResult=”COMMAND non trovato in “ + sKey
    - Esegue self.Log(sResult)
  - Se sResult=”” e self.sCommand diverso da “”:
    - Esegue self.Log1(“Eseguo il comando: “ + self.sCommand  + “, Sezione: ” + sKey + “, TS: “ + Timestamp() interno + “, Risultato: “ + sResult)
    - Esegue sResult=cbCommands(self.dictJob), funzione globale
    - Sostituisce il record in self.dictJobs corrispondente alla chiave sKey, con una copia del dizionario  self.dictJob
    - Esegue self.Log1(“Eseguito il comando: “ + self.sCommand  + “, Sezione: ” + sKey + “, TS: “ + Timestamp() interno + “, Risultato: “ + sResult)
  - Esci prima della fine del ciclo con un break, se self.bErrExit=True, saltando i jobs successivi.
Ritorna sResult.
Metodo End
Parametro sResult
Variabile locale bIsFatalError = False // Per gestire la scrittura del file.
Fase 1: Determinazione del Codice di Uscita e Preparazione Struttura
Se self.dictJobs è vuoto
- nResult = 1
- Questo blocco gestisce l'errore di Start: creiamo la struttura minima:
- Inizializza self.dictJobs per poter registrare l'errore e salvare il file
- self.dictJobs = {"CONFIG": {}}
- bIsFatalError = True // Attiviamo la logica di registrazione dell'errore

Dopodiche:
- Crea un dizionario dictTemp={} con queste 2 chiavi
  - “RETURN.TYPE” = “”
  - “RETURN.VALUE”=””
- Se sResult è diverso da “”:
  - nResult = 2
  - bIsFatalError = True
Logica di Aggiornamento dei Dati
Solo se c'è un errore, nResult=1 o nResult=2.
Se bIsFatalError è True, aggiungi queste 2 chiavi a dictTemp
- “RETURN.TYPE” = “E”
- “RETURN.VALUE”=sResult // Corretto la virgola in punto

In qualunque caso:
- Esegue il metodo self.AddTimestamp(dictTemp)

Propaga l'aggiornamento alla struttura principale
- Unisci self.dictJobs[“CONFIG”] con dictTemp, con priorità alle chiavi di dictTemp
Fase 3: Salvataggio, Log e Uscita
Salva nel file di self.sJobEnd, il contenuto di self.dictJobs nel formato di un file .ini, con
- sWriteRes=save_dict_to_ini interno (ex aiSys.save_dict_to_ini)(self.dictJobs, self.sJobEnd) (usa variabile separata per non sovrascrivere sResult)
- Se sWriteRes=””: Scrivi “Creato file “ + self.sJobEnd
Esegue self.Log(sResult, “Fine applicazione ” + self.sName)
Nel caso di nResult=0 non succede nulla.
Esci dall’applicazione ritornando  `sys.exit``(``nResult``)` se nResult diverso da zero
Metodi Log(), Log0(), Log1()
Sono solo un remapping di:
- self.Log: remapping di  self.jLog.Log()
- self.Log0: remapping di self.jLog.Log0()
- self.Log1: remapping di self.jLog.Log1()
Usando gli stessi parametri dei metodi rimappati di self.jLog
Metodo Exec
Serve ad eseguire una ntjobsapp esterna in background. La firma è:
- def Exec(self, sScript: str, dictConfig: Optional[Dict] = None, dictJobs: Optional[Dict] = None, sID: str = "") -> str
Parametri:
- sScript: script python della ntjobsapp esterna da eseguire (obbligatorio, deve esistere)
- dictConfig: dizionario config con le entries aggiuntive oltre a quelle previste per la ntjobsapp (facoltativo). Viene fuso sopra il default {"TYPE": "NTJOBS.APP.1"}. Tutte le chiavi convertite in MAIUSCOLO, valori convertiti in stringa (None="").
- dictJobs: dizionario dei jobs dove la chiave è la sezione e il valore è il dizionario del singolo job oppure un array di dizionari job. Se array, genera le sezioni NOME_01, NOME_02, ecc. Tutte le sezioni e chiavi convertite in MAIUSCOLO, valori in stringa (None="").
- sID: ID assegnato al job (obbligatorio). Ammessi solo lettere, numeri, "_" e "-".
Elaborazioni:
- sProc=NomeFunzione, sResult="" all'inizio.
- Se sID non valido: sResult=ErrorProc interno("sID non valido " + sID, sProc), scrivilo in console, ritorna sResult.
- Se sScript vuoto o file non esistente: stesso schema di errore.
- sFolder=cartella dello script (dirname di abspath di sScript normalizzato, oppure cwd).
- sIni=sFolder + "/ntjobsapp_[sID].ini", sEnd=sFolder + "/ntjobsapp_[sID].end", entrambi via NormalizePath.
- Costruisci data={"CONFIG": {...}} come sopra e salvalo con save_dict_to_ini interno in sIni. Se errore, ritorna ErrorProc interno.
- Cancella eventuale sEnd residuo di esecuzioni precedenti (ignora errori di cancellazione).
- Lancia in background senza bloccare il chiamante: subprocess.Popen([sys.executable, abspath(sScript), abspath(sIni)], cwd=sFolder, stdin=DEVNULL, stdout=DEVNULL, stderr=DEVNULL). Se fallisce, sResult=ErrorProc interno("Errore avvio script ...", sProc).
- Memorizza in self.dictExec[sID]={"INI": sIni, "END": sEnd, "SCRIPT": abspath(sScript), "TS": Timestamp() interno} (self.dictExec inizializzato come {} in __init__).
- Esegui self.Log1("Eseguita ntjobsapp esterna: " + sScript + ", ID: " + sID + ", File: " + sIni).
- Alla fine scrivi in console "Eseguita ntjobsapp." + sProc + ": " + sResult e ritorna ErrorProc interno(sResult, sProc) — di fatto "" se tutto bene.
- Nota: il file .ini creato è anche il parametro passato allo script eseguito.
- Richiede import subprocess in testa al file (solo stdlib).
Metodo ExecReturn
Serve a raccogliere il risultato della ntjobsapp esterna lanciata con Exec. La firma è:
- def ExecReturn(self, sID: str = "", nTimeout: int = 30) -> Dict
Parametri:
- sID: stesso ID usato in Exec (obbligatorio, stesse regole di validità).
- nTimeout: secondi di attesa del file .end (facoltativo, default 30). Se non convertibile a int, usa 30. Se <=0, un solo controllo senza attesa.
Elaborazioni:
- sProc=NomeFunzione.
- Se sID non valido: scrivi errore in console e ritorna {"ERROR": "sID non valido ..."}.
- Risolvi sEnd e sIni da self.dictExec[sID] se presente, altrimenti cwd + "/ntjobsapp_[sID].end" e cwd + "/ntjobsapp_[sID].ini" (NormalizePath).
- Attendi fino a deadline=time.time() + max(0, nTimeout) con poll ogni 0.5 secondi: se il file sEnd esiste esci dal ciclo; se scade il timeout scrivi in console "Eseguita ntjobsapp." + sProc + ": " e ritorna {} (dizionario vuoto = lavoro non finito, numero chiavi 0).
- Se il file esiste: attendi 0.2 secondi (flush scrittura), leggilo con read_ini_to_dict interno. Se errore di lettura: scrivi errore in console e ritorna {"ERROR": sReadErr} senza cancellare.
- Se lettura ok: cancella sEnd e sIni se esistono (ignora errori), rimuovi self.dictExec[sID], esegui self.Log1("Letto risultato ntjobsapp esterna ID: " + sID + ", Sezioni: " + numero), scrivi in console "Eseguita ntjobsapp." + sProc + ": " e ritorna dictResult (numero chiavi > 0 = finito, contiene il file ntjobsapp_[sID].end letto).
- In caso di eccezione generica: scrivi errore in console e ritorna {"ERROR": str(e)}.
- Richiede import time in testa al file (già presente).
