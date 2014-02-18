; Close a previous instance of this script
; WinKill("closeJavaMsgBox.exe", "") ; Doesn't work
; Close the "Java(TM) Platform SE binary has stopped working" message box
While WinExists("Brood War")
    WinClose("Java(TM) Platform SE binary", "")
    Sleep(1000)
WEnd