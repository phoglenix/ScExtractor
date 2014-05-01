#!/bin/bash

# Get the replay folder setting from extractor configuration file.
replayFolder=`grep -E "replay_folder\s*=" extractorConfig.properties | cut -d = -f 2`
echo Using replay folder: $replayFolder

# Ensure there are replay files to operate upon
while [ "`ls $replayFolder/*.rep`" ]; do
  # Clean up the logs
  echo Cleaning up logs
  outputFile=javabot-impt-`date +%y%m%d-%H%M%S`.log
  ls -rv javabot.log.* | xargs grep -e "INFO\|WARNING\|SEVERE" -C 10 > $outputFile
  rm javabot.log.*
  if [ ! -s $outputFile ]; then
    # Delete empty output files
    rm $outputFile
  fi
  # Make sure Starcraft is closed
  sleep 2
  ./closeStarcraft.exe
  # Run (have to use cygpath to get the windows paths which java is expecting)
  echo Starting Starcraft
  java -Xmx512m -Xms512m -ea -cp `cygpath -wp ./bin:../mysql-connector-java-5.1.24-bin.jar` extractor.ExtractStates &
  javaPID=$!
  
  sleep 4
  # Close and reopen Starcraft so the client connects
  ./closeReopenStarcraft.exe
  # Make sure the "Java(TM) Platform SE binary has stopped working" message box isn't holding up the works
  ./closeJavaMsgBox.exe &
  # Make sure we're not stuck at "Bridge: Waiting to enter match..." by checking that the log files are constantly changing
  # - if not happening then it hasn't connected properly, so keep closing/opening Starcraft
  ./closeStarcraftIfWaiting.exe &
  
  # Wait for Java process to stop
  wait $javaPID
  echo ==Java Exited==
  echo
done
echo No replay files left to process