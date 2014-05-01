; Quit
ConsoleWrite("closeStarcraft: closing starcraft" & @Lf)
;Send Alt-F4, x (don't think this command actually works)
ControlSend ("Brood War", "", "Brood War", "!{F4}x" )
; Force kill
WinKill ("Brood War")
; Wait to close (give 1 sec after)
WinWaitClose("Brood War")
Sleep(1000)
ConsoleWrite("closeStarcraft: done" & @Lf)