Local $success = 0
;Do
;  ; Wait for Starcraft to become active
;  WinActivate("Brood War")
;  $success = WinWaitActive("Brood War", "", 3)
;Until NOT $success = 0

; Quit
ConsoleWrite("closeReopenStarcraft: closing starcraft" & @Lf)
;Send Alt-F4, x (don't think this command actually works)
ControlSend ("Brood War", "", "Brood War", "!{F4}x" )
; Force kill
WinKill ("Brood War")
; Wait to close (give 1 sec after)
WinWaitClose("Brood War")
Sleep(1000)

;Do
;  ; Activate Chaoslauncher window
;  WinActivate("Chaoslauncher")
;  $success = WinWaitActive("Chaoslauncher", "", 3)
;Until NOT $success = 0

; Press Start
ConsoleWrite("closeReopenStarcraft: opening starcraft" & @Lf)
ControlClick("Chaoslauncher", "", "[TEXT:Start]")
$loops = 0
Do
  $loops += 1
  If WinExists("Exception", "Starcraft is already running") Then
    WinClose("Exception", "Starcraft is already running")
  EndIf
  If $loops == 10 Then
    ControlClick("Chaoslauncher", "", "[TEXT:Start]")
    $loops = 0
  EndIf
  ; Wait for Starcraft to open
  $success = WinWait("Brood War", "", 3)
Until NOT $success = 0
ConsoleWrite("closeReopenStarcraft: done" & @Lf)