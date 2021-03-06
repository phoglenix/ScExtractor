;#include <Constants.au3>
;#Include <Date.au3>
; Check the last-modified time of the log file to find cases of sitting in the state:
; "Bridge: Waiting to enter match..."
$waitTimeInMs = 1000 * 60 * 3 ; time to start up / restart starcraft and begin recording
$idleTimeInSecs = 60 * 5 ; time to wait for a change in the log files before concluding Starcraft is idle
$logFile = "javabot.log.0"
$closeReopenStarCraft = "closeReopenStarcraft.exe"
$offset = 0
Sleep($waitTimeInMs) ; time to start up starcraft
While WinExists("Brood War")
    ; Ensure there's only one copy of this running
    $copies = ProcessList("closeStarcraftIfWaiting.exe")
    If (NOT @error) AND $copies[0][0] > 1 Then
        ; Exit if we're not the one with the lowest PID (avoids race conditions)
        $lowestPID = -1
        For $i = 1 To $copies[0][0]
            If $lowestPID == -1 OR $copies[$i][1] < $lowestPID Then
                $lowestPID = $copies[$i][1]
            EndIf
        Next
        If Not(@AutoItPID == $lowestPID) Then
            Exit(0)
        EndIf
    EndIf
    ; TODO CHECK FILE SIZE INSTEAD OF MODIFICATION DATE?
    ; Refresh folder first, not sure if this helps
    ControlSend ("ScExtractor", "", "[CLASS:DirectUIHWND; INSTANCE:3]", "{BROWSER_REFRESH}")
    ; Check last mod time of log vs current time
    $logfileTime = FileGetTime($logFile, 0, 1)
    If @error Then
        ContinueLoop
    EndIf
    $currentTime = @YEAR & @MON & @MDAY & @HOUR & @MIN & @SEC
    ; difference in seconds
    $diff = $currentTime - $logfileTime + $offset
    ; sometimes difference is negative. It seems that autoit gets an incorrect date somehow
    If $diff < 0 Then
        $offset = $offset - $diff
    EndIf
    ; and sometimes autoit reverts to the correct date :S
    If $diff > ($idleTimeInSecs * 10) AND $offset > 0 Then
        $offset = 0
        $diff = 0
    EndIf
    ; MsgBox(0, "Output:", "current " & $currentTime & @LF & _
            ; "logfile " & $logfileTime & @LF & _
            ; "offset " & $offset & @LF & _
            ; "diff " & $diff)
    
    ; wait until nothing is added to the log for $idleTimeInSecs
    If $diff > $idleTimeInSecs Then
        ConsoleWrite("closeStarcraftIfWaiting: closing starcraft" & @Lf)
        $command = @WorkingDir & "\" & $closeReopenStarCraft
        Run($command)
        ; Give some time to start running again
        Sleep($waitTimeInMs)
    EndIf

    Sleep(1000) ; 1 sec
WEnd