Local $success = 0
;Do
;  ; Wait for Starcraft to become active
;  WinActivate("Brood War")
;  $success = WinWaitActive("Brood War", "", 3)
;Until NOT $success = 0

; Quit
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
ControlClick("Chaoslauncher", "", "[TEXT:Start]")
Do
  ; Wait for Starcraft to open
  $success = WinWait("Brood War", "", 3)
Until NOT $success = 0