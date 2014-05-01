/* Show counts of each OrderType used */
SELECT temp.counts, temp.OrderTypeId, ordertype.name
FROM ordertype
JOIN (
  SELECT count( * ) AS counts, action.OrderTypeId
  FROM ACTION GROUP BY OrderTypeId
) temp ON temp.ordertypeid = ordertype.ordertypeid
ORDER BY temp.counts DESC

/* Show counts of each UnitCommandType used */
SELECT temp.counts, temp.UnitCommandTypeId, unitcommandtype.name
FROM unitcommandtype
JOIN (
  SELECT count( * ) AS counts, action.UnitCommandTypeId
  FROM ACTION GROUP BY UnitCommandTypeId
) temp ON temp.unitcommandtypeid = unitcommandtype.unitcommandtypeid
ORDER BY temp.counts DESC

/* At the console */
show full processlist;

use information_schema;
select table_name,column_name,constraint_name,referenced_table_name,referenced_column_name from key_column_usage where referenced_table_name='region';

/* Viewing walkability from binary value */
SELECT walktileid, mapid, btileposx, btileposy, LPAD(BIN(walkability), 16, '0') FROM `walktile`

/* Find replays with invalid units */
SELECT replayid, count( * )
FROM `unit`
NATURAL JOIN playerreplay
WHERE `UnitTypeID` =228
GROUP BY replayid
ORDER BY count( * ) DESC

-- bad replays (have "none" type units left) - usually broken, wrong version probably
SELECT count(*), playerreplay.replayid, replayname FROM `unit` natural join playerreplay natural join replay WHERE `UnitTypeID`=228 group by replayid ORDER BY count(*)  DESC

-- bad replays with < 30 "none" type units
SELECT * FROM (SELECT count(*) AS c, playerreplay.replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay WHERE `UnitTypeID`=228 GROUP BY replayid) t WHERE c<30

-- bad replays ordered by proportion of "none" type units
SELECT invalids, totalUnits, (invalids/totalUnits), replayid, replayname FROM (SELECT count(*) AS invalids, replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay WHERE `UnitTypeID`=228 GROUP BY replayid) t1 NATURAL JOIN (SELECT count(*) AS totalUnits, replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay GROUP BY replayid) t2 ORDER BY `(invalids/totalUnits)`  DESC

-- bad replays with more than 1% "none" type units
SELECT invalids, totalUnits, (invalids/totalUnits), replayid, replayname FROM (SELECT count(*) AS invalids, replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay WHERE `UnitTypeID`=228 GROUP BY replayid) t1 NATURAL JOIN (SELECT count(*) AS totalUnits, replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay GROUP BY replayid) t2 WHERE (invalids/totalUnits)>0.01 ORDER BY (invalids/totalUnits)  DESC;

-- set @repname to one of the replays with more than 1% "none" type units
SELECT replayname FROM (SELECT count(*) AS invalids, replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay WHERE `UnitTypeID`=228 GROUP BY replayid) t1 NATURAL JOIN (SELECT count(*) AS totalUnits, replayid, replayname FROM `unit` NATURAL JOIN playerreplay NATURAL JOIN replay GROUP BY replayid) t2 WHERE (invalids/totalUnits)>0.01 LIMIT 1 into @repname;

-- set @repname to the one with the most "none" type units
-- SELECT replayname FROM `unit` natural join playerreplay natural join replay WHERE `UnitTypeID`=228 group by replayid ORDER BY count(*)  DESC LIMIT 1 into @repname;
-- OR choose a specific replayname (avoiding stupid collaction issues):
-- SELECT replayname FROM replay WHERE replayname='GG123.rep' into @repname;

/* delete a replay (broken down into smaller steps and not using cascade so it goes faster) */
select min(playerreplayid) from playerreplay natural join replay where replayname=@repname into @prid;
delete attributechange from attributechange natural join unit natural join playerreplay natural join replay where replayname=@repname and playerreplayid=@prid;
select max(playerreplayid) from playerreplay natural join replay where replayname=@repname into @prid;
delete attributechange from attributechange natural join unit natural join playerreplay natural join replay where replayname=@repname and playerreplayid=@prid;
delete attributechange from attributechange natural join unit natural join playerreplay natural join replay where replayname=@repname;
delete action from action natural join playerreplay natural join replay where replayname=@repname;
delete regionvaluechange from regionvaluechange natural join playerreplay natural join replay where replayname=@repname;
delete unit from unit natural join playerreplay natural join replay where replayname=@repname;
delete from replay where replayname=@repname;
-- finally, remove any orphaned maps
delete map FROM map natural left join replay WHERE hash is null or replay.mapid is null;

/* Killing Orphans (selects should return 0 results) */
-- broken/uninitialised build tiles
select * from replay where mapid in (SELECT distinct(mapid) FROM `buildtile` WHERE regionid is null)

-- orphan playerreplays' units
select * from unit where playerreplayid in (select playerreplayid FROM playerreplay WHERE replayid in (SELECT replayid FROM (select replay.replayid from replay left join playerreplay on replay.replayid=playerreplay.replayid group by replay.replayid having count(replay.replayid)=1) t )  )
-- delete from unit where playerreplayid in (select playerreplayid FROM playerreplay WHERE replayid in (SELECT replayid FROM (select replay.replayid from replay left join playerreplay on replay.replayid=playerreplay.replayid group by replay.replayid having count(replay.replayid)=1) t )  )

-- orphan unitgroups
SELECT * FROM unitgroup left join action using (unitgroupid) where action.actionid is null
-- DELETE unitgroup FROM unitgroup LEFT JOIN ACTION USING ( unitgroupid ) WHERE action.actionid IS NULL 

-- playerreplays with no actions
SELECT * FROM playerreplay left join action on playerreplay.playerreplayid=action.playerreplayid WHERE action.playerreplayid is null and playername!="Neutral"

-- orphan playerreplays
select * FROM playerreplay WHERE replayid in (SELECT replayid FROM (select replay.replayid from replay left join playerreplay on replay.replayid=playerreplay.replayid group by replay.replayid having count(replay.replayid)=1) t )
-- delete FROM playerreplay WHERE replayid in (SELECT replayid FROM (select replay.replayid from replay left join playerreplay on replay.replayid=playerreplay.replayid group by replay.replayid having count(replay.replayid)=1) t )

-- orphans in replays
SELECT * FROM replay left join playerreplay on replay.replayid=playerreplay.replayid WHERE playerreplay.replayid is null
-- delete FROM replay WHERE replayid in (SELECT replayid FROM (SELECT replay.replayid FROM replay left join playerreplay on replay.replayid=playerreplay.replayid WHERE playerreplay.replayid is null) t )

-- replays with <3 players (incl. neutral)
SELECT * FROM replay natural join playerreplay group by replayid having count(replayid)<3

-- maps with uninitialised build tiles
select * from replay where mapid in (SELECT distinct(mapid) FROM `buildtile` WHERE regionid is null)

-- replays with unititialised maps
SELECT * FROM map left join replay on map.mapid=replay.mapid WHERE hash is null or replay.mapid is null

-- orphans in maps
SELECT * FROM map left join replay on map.mapid=replay.mapid WHERE hash is null or replay.mapid is null
DELETE map FROM map left join replay on map.mapid=replay.mapid WHERE hash is null or replay.mapid is null


/* Find numbers of each race used in each replay (for finding if a replay is in the correct DB) */
SELECT replayid, count(*) as c, t, p, z, n FROM playerreplay NATURAL LEFT JOIN (SELECT replayid, count(*) as t FROM playerreplay WHERE raceid=1 GROUP BY replayid) tt NATURAL LEFT JOIN (SELECT replayid, count(*) as p FROM playerreplay WHERE raceid=2 GROUP BY replayid) pt NATURAL LEFT JOIN (SELECT replayid, count(*) as z FROM playerreplay WHERE raceid=0 GROUP BY replayid) zt NATURAL LEFT JOIN (SELECT replayid, count(*) as n FROM playerreplay WHERE raceid=5 GROUP BY replayid) nt GROUP BY replayid

/*
Backup database to h: 
mysqldump -u root -p123 --all-databases | gzip > H:\MySQLData.sql.gz
Restore database from h:
gzip -d < H:\MySQLData.sql.gz | ..\bin\mysql.exe -u root -p123
*/
