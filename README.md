# Coffee Monitor Service ☕📡

Servizio di monitoraggio dei distributori automatici sviluppato in Jakarta EE (Servlet + JDBC).
Il servizio è indipendente dal sistema principale e utilizza un database separato per memorizzare
lo stato di funzionamento, la posizione e i segnali di heartbeat dei distributori.

È progettato per essere semplice, robusto e best-effort, senza autenticazione, così da poter essere
utilizzato anche da applicazioni esterne (mappe, dashboard, client).

----------------------------------------------------------------

## Architettura

- Tecnologia: Jakarta EE (Servlet API 6)
- Server: Apache Tomcat 10.1+
- Database: MySQL (database dedicato `coffee_monitor`)
- Porta di default: 8081
- Context path: /coffee-monitor

Il servizio non espone interfacce grafiche.
Espone esclusivamente endpoint HTTP per il monitoraggio.

----------------------------------------------------------------

## Ruolo del servizio di monitoraggio

Il servizio di monitoraggio NON gestisce:
- utenti
- autenticazione
- ruoli
- sessioni

Il suo unico scopo è:
- mantenere una copia dello stato dei distributori
- ricevere heartbeat
- rilevare automaticamente i guasti
- fornire una vista globale dello stato dei distributori

Il sistema principale rimane l’unica fonte di verità per la gestione applicativa.

----------------------------------------------------------------

## Modalità di sincronizzazione

Il servizio lavora con due modalità di sincronizzazione distinte.

### 1) Sincronizzazione incrementale automatica (principale)

È la modalità normale di funzionamento.

Ogni volta che nel sistema principale:
- viene creato un distributore
- viene eliminato un distributore
- cambia lo stato di un distributore (manager o manutentore)

il sistema principale invia automaticamente l’aggiornamento al servizio di monitoraggio
tramite chiamate HTTP best-effort.

Questa sincronizzazione è già attiva e non richiede interventi manuali.

### 2) Sincronizzazione bulk manuale (riallineamento)

Serve solo in casi particolari:
- primo avvio del servizio di monitoraggio
- riallineamento dopo downtime
- recovery da errori
- verifica manuale da parte dell’amministratore

In questo caso il sistema principale legge tutti i distributori dal proprio database
e invia l’elenco completo al servizio di monitoraggio.

Questa operazione è manuale (ad esempio tramite pulsante nel pannello admin).

----------------------------------------------------------------

## Heartbeat e rilevamento guasti

### Heartbeat

Ogni distributore invia periodicamente un segnale di heartbeat.

Effetti:
- aggiorna il campo `last_seen`
- se il distributore non esiste nel monitor, viene creato automaticamente (best-effort)

### Rilevamento automatico FAULT

Un distributore viene marcato come FAULT se:
- non è in stato MAINTENANCE
- non ha inviato heartbeat negli ultimi 3 minuti

Il controllo viene eseguito automaticamente quando viene richiesta la mappa
e non più di una volta ogni 30 secondi per evitare overload sul database.

----------------------------------------------------------------

## Endpoint esposti dal servizio

### POST /api/monitor/heartbeat

Riceve il segnale di vita di un distributore.

Parametri (application/x-www-form-urlencoded):
- code (obbligatorio)

Esempio:
POST /api/monitor/heartbeat
code=D-001

Risposta:
{ "ok": true }

----------------------------------------------------------------

### POST /api/monitor/distributors/create

Crea o aggiorna (upsert) un distributore nel database di monitoraggio.

Parametri:
- code (obbligatorio)
- location_name (opzionale)
- status (ACTIVE | MAINTENANCE | FAULT)

Se il distributore esiste viene aggiornato.
Se non esiste viene creato.

----------------------------------------------------------------

### POST /api/monitor/distributors/delete

Elimina un distributore dal database di monitoraggio.

Parametri:
- code (obbligatorio)

Se il distributore non esiste, l’operazione viene comunque considerata riuscita
(best-effort, non bloccante).

----------------------------------------------------------------

### POST /api/monitor/distributors/status

Aggiorna lo stato di un distributore.

Parametri:
- code (obbligatorio)
- status (ACTIVE | MAINTENANCE | FAULT)

----------------------------------------------------------------

### POST /api/monitor/sync

Sincronizzazione bulk manuale.

Body JSON:
{
"items": [
{
"code": "D-001",
"location_name": "Edificio A",
"status": "ACTIVE"
}
]
}

Questo endpoint viene chiamato dal sistema principale per riallineare completamente
il database di monitoraggio con quello principale.

----------------------------------------------------------------

### GET /api/monitor/map

Restituisce lo stato globale di tutti i distributori.

Risposta:
{
"ok": true,
"items": [
{
"code": "D-001",
"location_name": "Edificio A",
"status": "ACTIVE",
"last_seen": "2025-01-01 12:00:00"
}
]
}

Questo endpoint:
- ricalcola automaticamente i distributori in FAULT
- è pensato per dashboard, mappe e monitoraggio centralizzato

----------------------------------------------------------------

## Sicurezza

- Nessuna autenticazione
- Nessuna gestione ruoli
- CORS aperto
- Database separato dal sistema principale

La sicurezza è demandata al sistema principale,
che funge da proxy quando necessario.

----------------------------------------------------------------

## Configurazione database

Variabili d’ambiente supportate:
- MONITOR_DB_URL
- MONITOR_DB_USER
- MONITOR_DB_PASSWORD

Valori di default:
jdbc:mysql://localhost:3306/coffee_monitor
monitor_user / MonitorPass123!

----------------------------------------------------------------

## Riassunto

Il servizio di monitoraggio:
- è indipendente dal sistema principale
- utilizza un database separato
- riceve aggiornamenti automatici incrementali
- supporta una sincronizzazione bulk manuale
- rileva automaticamente i guasti
- non blocca mai il sistema principale (best-effort)

È progettato per essere semplice, coerente e pienamente comprensibile
in un contesto didattico basato su Servlet e JDBC.
