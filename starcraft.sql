-- phpMyAdmin SQL Dump
-- version 3.4.5
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Oct 08, 2013 at 10:38 PM
-- Server version: 5.5.16
-- PHP Version: 5.3.8

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- === IMPORTING INSTRUCTIONS ==
-- Create new database [DBNAME] with collation utf8_unicode_ci
-- Import using: mysql.exe -u root -p123 [DBNAME] < starcraft.sql
--         then: mysql.exe -u root -p123 [DBNAME] < starcraft-staticdata.sql

-- --------------------------------------------------------

--
-- Table structure for table `action`
--

CREATE TABLE IF NOT EXISTS `action` (
  `ActionID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PlayerReplayID` int(11) NOT NULL,
  `Frame` int(11) NOT NULL,
  `UnitCommandTypeID` smallint(6) NOT NULL COMMENT 'Exactly one of UnitCommandTypeId and OrderTypeId will be set to None.',
  `OrderTypeID` smallint(6) NOT NULL COMMENT 'Exactly one of UnitCommandTypeId and OrderTypeId will be set to None.',
  `UnitGroupID` bigint(20) NOT NULL,
  `TargetID` int(11) NOT NULL DEFAULT '-1' COMMENT 'replayID of a unit, or ID of a unitType, techType, or upgradeType depending on the action. -1 if unused',
  `TargetX` smallint(6) NOT NULL DEFAULT '-1' COMMENT '-1 if unused',
  `TargetY` smallint(6) NOT NULL DEFAULT '-1' COMMENT '-1 if unused',
  `Delayed` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ActionID`),
  KEY `PlayerReplayID` (`PlayerReplayID`),
  KEY `Frame` (`Frame`),
  KEY `UnitCommandTypeID` (`UnitCommandTypeID`),
  KEY `OrderTypeID` (`OrderTypeID`),
  KEY `UnitGroupID` (`UnitGroupID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `attributechange`
--

CREATE TABLE IF NOT EXISTS `attributechange` (
  `AttributeChangeID` bigint(20) NOT NULL AUTO_INCREMENT,
  `UnitID` bigint(20) NOT NULL,
  `AttributeTypeID` smallint(6) NOT NULL,
  `ChangeVal` int(11) NOT NULL,
  `ChangeTime` int(11) NOT NULL,
  PRIMARY KEY (`AttributeChangeID`),
  UNIQUE KEY `usual_query` (`UnitID`,`AttributeTypeID`,`ChangeTime`),
  KEY `AttributeTypeID` (`AttributeTypeID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `attributetype`
--

CREATE TABLE IF NOT EXISTS `attributetype` (
  `AttributeTypeID` smallint(6) NOT NULL,
  `AttributeName` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`AttributeTypeID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `buildtile`
--

CREATE TABLE IF NOT EXISTS `buildtile` (
  `BuildTileID` bigint(20) NOT NULL AUTO_INCREMENT,
  `MapID` int(11) NOT NULL,
  `BTilePosX` smallint(6) NOT NULL,
  `BTilePosY` smallint(6) NOT NULL,
  `GroundHeightID` tinyint(4) NOT NULL DEFAULT '0',
  `Buildable` tinyint(1) NOT NULL DEFAULT '-1',
  `Walkable` smallint(5) unsigned NOT NULL DEFAULT '0' COMMENT 'Note 16 binary values, ordering is in columns, not rows',
  `ChokeDist` int(11) NOT NULL DEFAULT '-1',
  `BaseLocationDist` int(11) NOT NULL DEFAULT '-1',
  `StartLocationDist` int(11) NOT NULL DEFAULT '-1',
  `RegionID` int(11) NOT NULL,
  PRIMARY KEY (`BuildTileID`),
  UNIQUE KEY `usual_query` (`MapID`,`BTilePosX`,`BTilePosY`),
  KEY `GroundHeightID` (`GroundHeightID`),
  KEY `RegionID` (`RegionID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `event`
--

CREATE TABLE IF NOT EXISTS `event` (
  `EventID` int(11) NOT NULL AUTO_INCREMENT,
  `ReplayID` int(11) NOT NULL,
  `Frame` int(11) NOT NULL,
  `EventTypeID` tinyint(4) NOT NULL,
  `UnitID` bigint(20) DEFAULT NULL,
  `BuildTileID` bigint(20) DEFAULT NULL COMMENT 'For NukeDetect',
  PRIMARY KEY (`EventID`),
  KEY `EventTypeID` (`EventTypeID`),
  KEY `UnitID` (`UnitID`),
  KEY `BuildTileID` (`BuildTileID`),
  KEY `usual_query` (`ReplayID`,`Frame`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci COMMENT='Note not all event types are recorded here.' AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `eventtype`
--

CREATE TABLE IF NOT EXISTS `eventtype` (
  `EventTypeID` tinyint(4) NOT NULL,
  `EventTypeName` varchar(20) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`EventTypeID`),
  KEY `Name` (`EventTypeName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `groundheight`
--

CREATE TABLE IF NOT EXISTS `groundheight` (
  `GroundHeightID` tinyint(4) NOT NULL,
  `GroundHeightName` varchar(20) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`GroundHeightID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `map`
--

CREATE TABLE IF NOT EXISTS `map` (
  `MapID` int(11) NOT NULL AUTO_INCREMENT,
  `MapName` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  `Hash` varchar(40) COLLATE utf8_unicode_ci NOT NULL COMMENT 'from BWTA',
  `NumStartPos` tinyint(4) NOT NULL DEFAULT '-1',
  PRIMARY KEY (`MapID`),
  UNIQUE KEY `Hash` (`Hash`),
  UNIQUE KEY `usual_query` (`MapName`,`Hash`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `ordertype`
--

CREATE TABLE IF NOT EXISTS `ordertype` (
  `OrderTypeID` smallint(6) NOT NULL,
  `OrderName` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`OrderTypeID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `playerreplay`
--

CREATE TABLE IF NOT EXISTS `playerreplay` (
  `PlayerReplayID` int(11) NOT NULL AUTO_INCREMENT,
  `PlayerName` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  `Winner` tinyint(1) NOT NULL COMMENT 'May not be true for either player if not clear in replay',
  `RaceID` tinyint(4) NOT NULL,
  `ReplayID` int(11) NOT NULL,
  `StartPosBTID` bigint(20) DEFAULT NULL COMMENT 'A buildTile. Nullable for ExtractActions and Neutral players',
  PRIMARY KEY (`PlayerReplayID`),
  KEY `ReplayID` (`ReplayID`),
  KEY `StartPositionID` (`StartPosBTID`),
  KEY `RaceID` (`RaceID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `race`
--

CREATE TABLE IF NOT EXISTS `race` (
  `RaceID` tinyint(4) NOT NULL,
  `RaceName` varchar(20) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`RaceID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `region`
--

CREATE TABLE IF NOT EXISTS `region` (
  `RegionID` int(11) NOT NULL AUTO_INCREMENT,
  `MapID` int(11) NOT NULL,
  `ScRegionID` int(11) NOT NULL,
  PRIMARY KEY (`RegionID`),
  UNIQUE KEY `usual_query` (`MapID`,`ScRegionID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `regionvaluechange`
--

CREATE TABLE IF NOT EXISTS `regionvaluechange` (
  `ChangeID` int(11) NOT NULL AUTO_INCREMENT,
  `PlayerReplayID` int(11) NOT NULL,
  `RegionID` int(11) NOT NULL,
  `Frame` int(11) NOT NULL DEFAULT '-1',
  `GroundUnitValue` int(11) NOT NULL DEFAULT '-1',
  `BuildingValue` int(11) NOT NULL DEFAULT '-1',
  `AirUnitValue` int(11) NOT NULL DEFAULT '-1',
  `EnemyGroundUnitValue` int(11) NOT NULL DEFAULT '-1',
  `EnemyBuildingValue` int(11) NOT NULL DEFAULT '-1',
  `EnemyAirUnitValue` int(11) NOT NULL DEFAULT '-1',
  `ResourceValue` int(11) NOT NULL DEFAULT '-1',
  PRIMARY KEY (`ChangeID`),
  UNIQUE KEY `usual_query` (`PlayerReplayID`,`RegionID`,`Frame`),
  KEY `RegionID` (`RegionID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `replay`
--

CREATE TABLE IF NOT EXISTS `replay` (
  `ReplayID` int(11) NOT NULL AUTO_INCREMENT,
  `MapID` int(11) DEFAULT NULL COMMENT 'Nullable for ExtractActions',
  `ReplayName` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `Duration` int(11) NOT NULL,
  PRIMARY KEY (`ReplayID`),
  UNIQUE KEY `ReplayName` (`ReplayName`),
  KEY `MapID` (`MapID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `resourcechange`
--

CREATE TABLE IF NOT EXISTS `resourcechange` (
  `ChangeID` int(11) NOT NULL AUTO_INCREMENT,
  `PlayerReplayID` int(11) NOT NULL,
  `Frame` int(11) NOT NULL,
  `Minerals` int(11) NOT NULL DEFAULT '-1',
  `Gas` int(11) NOT NULL DEFAULT '-1',
  `Supply` int(11) NOT NULL DEFAULT '-1',
  `TotalMinerals` int(11) NOT NULL DEFAULT '-1',
  `TotalGas` int(11) NOT NULL DEFAULT '-1',
  `TotalSupply` int(11) NOT NULL DEFAULT '-1',
  PRIMARY KEY (`ChangeID`),
  UNIQUE KEY `usual_query` (`PlayerReplayID`,`Frame`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `unit`
--

CREATE TABLE IF NOT EXISTS `unit` (
  `UnitID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PlayerReplayID` int(11) NOT NULL,
  `UnitTypeID` smallint(6) NOT NULL DEFAULT '228',
  `UnitReplayID` int(16) NOT NULL DEFAULT '-1' COMMENT 'The replayID attribute of the unit',
  PRIMARY KEY (`UnitID`),
  UNIQUE KEY `usual_query` (`PlayerReplayID`,`UnitReplayID`),
  KEY `UnitTypeID` (`UnitTypeID`),
  KEY `UnitReplayID` (`UnitReplayID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `unitcommandtype`
--

CREATE TABLE IF NOT EXISTS `unitcommandtype` (
  `UnitCommandTypeID` smallint(6) NOT NULL,
  `UnitCommandName` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`UnitCommandTypeID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `unitgroup`
--

CREATE TABLE IF NOT EXISTS `unitgroup` (
  `UnitGroupID` bigint(20) NOT NULL AUTO_INCREMENT,
  `UnitID` bigint(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`UnitGroupID`,`UnitID`),
  KEY `UnitID` (`UnitID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci COMMENT='Can be >12 units in a group if one dies' AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `unittype`
--

CREATE TABLE IF NOT EXISTS `unittype` (
  `UnitTypeID` smallint(6) NOT NULL,
  `UnitTypeName` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`UnitTypeID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `visibilitychange`
--

CREATE TABLE IF NOT EXISTS `visibilitychange` (
  `VisibilityChangeID` bigint(20) NOT NULL AUTO_INCREMENT,
  `ViewerID` int(11) NOT NULL,
  `UnitID` bigint(11) NOT NULL,
  `ChangeTime` int(11) NOT NULL,
  `ChangeVal` tinyint(4) NOT NULL,
  PRIMARY KEY (`VisibilityChangeID`),
  UNIQUE KEY `usual_query` (`ViewerID`,`UnitID`,`ChangeTime`),
  KEY `ViewerID` (`ViewerID`),
  KEY `UnitID` (`UnitID`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=0 ;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `action`
--
ALTER TABLE `action`
  ADD CONSTRAINT `action_ibfk_4` FOREIGN KEY (`UnitCommandTypeID`) REFERENCES `unitcommandtype` (`UnitCommandTypeID`),
  ADD CONSTRAINT `action_ibfk_5` FOREIGN KEY (`OrderTypeID`) REFERENCES `ordertype` (`OrderTypeID`),
  ADD CONSTRAINT `action_ibfk_6` FOREIGN KEY (`UnitGroupID`) REFERENCES `unitgroup` (`UnitGroupID`) ON DELETE CASCADE,
  ADD CONSTRAINT `action_ibfk_7` FOREIGN KEY (`PlayerReplayID`) REFERENCES `playerreplay` (`PlayerReplayID`) ON DELETE CASCADE;

--
-- Constraints for table `attributechange`
--
ALTER TABLE `attributechange`
  ADD CONSTRAINT `attributechange_ibfk_3` FOREIGN KEY (`UnitID`) REFERENCES `unit` (`UnitID`) ON DELETE CASCADE,
  ADD CONSTRAINT `attributechange_ibfk_4` FOREIGN KEY (`AttributeTypeID`) REFERENCES `attributetype` (`AttributeTypeID`);

--
-- Constraints for table `buildtile`
--
ALTER TABLE `buildtile`
  ADD CONSTRAINT `buildtile_ibfk_4` FOREIGN KEY (`GroundHeightID`) REFERENCES `groundheight` (`GroundHeightID`) ON DELETE CASCADE,
  ADD CONSTRAINT `buildtile_ibfk_5` FOREIGN KEY (`MapID`) REFERENCES `map` (`MapID`) ON DELETE CASCADE,
  ADD CONSTRAINT `buildtile_ibfk_6` FOREIGN KEY (`RegionID`) REFERENCES `region` (`RegionID`) ON DELETE CASCADE;

--
-- Constraints for table `event`
--
ALTER TABLE `event`
  ADD CONSTRAINT `event_ibfk_4` FOREIGN KEY (`ReplayID`) REFERENCES `replay` (`ReplayID`) ON DELETE CASCADE,
  ADD CONSTRAINT `event_ibfk_5` FOREIGN KEY (`EventTypeID`) REFERENCES `eventtype` (`EventTypeID`),
  ADD CONSTRAINT `event_ibfk_6` FOREIGN KEY (`UnitID`) REFERENCES `unit` (`UnitID`) ON DELETE CASCADE,
  ADD CONSTRAINT `event_ibfk_7` FOREIGN KEY (`BuildTileID`) REFERENCES `buildtile` (`BuildTileID`) ON DELETE CASCADE;

--
-- Constraints for table `playerreplay`
--
ALTER TABLE `playerreplay`
  ADD CONSTRAINT `playerreplay_ibfk_19` FOREIGN KEY (`RaceID`) REFERENCES `race` (`RaceID`),
  ADD CONSTRAINT `playerreplay_ibfk_20` FOREIGN KEY (`ReplayID`) REFERENCES `replay` (`ReplayID`) ON DELETE CASCADE,
  ADD CONSTRAINT `playerreplay_ibfk_21` FOREIGN KEY (`StartPosBTID`) REFERENCES `buildtile` (`BuildTileID`);

--
-- Constraints for table `region`
--
ALTER TABLE `region`
  ADD CONSTRAINT `region_ibfk_1` FOREIGN KEY (`MapID`) REFERENCES `map` (`MapID`) ON DELETE CASCADE;

--
-- Constraints for table `regionvaluechange`
--
ALTER TABLE `regionvaluechange`
  ADD CONSTRAINT `regionvaluechange_ibfk_1` FOREIGN KEY (`PlayerReplayID`) REFERENCES `playerreplay` (`PlayerReplayID`) ON DELETE CASCADE,
  ADD CONSTRAINT `regionvaluechange_ibfk_2` FOREIGN KEY (`RegionID`) REFERENCES `region` (`RegionID`) ON DELETE CASCADE;

--
-- Constraints for table `replay`
--
ALTER TABLE `replay`
  ADD CONSTRAINT `replay_ibfk_1` FOREIGN KEY (`MapID`) REFERENCES `map` (`MapID`) ON DELETE CASCADE;

--
-- Constraints for table `resourcechange`
--
ALTER TABLE `resourcechange`
  ADD CONSTRAINT `resourcechange_ibfk_1` FOREIGN KEY (`PlayerReplayID`) REFERENCES `playerreplay` (`PlayerReplayID`) ON DELETE CASCADE;

--
-- Constraints for table `unit`
--
ALTER TABLE `unit`
  ADD CONSTRAINT `unit_ibfk_2` FOREIGN KEY (`UnitTypeID`) REFERENCES `unittype` (`UnitTypeID`),
  ADD CONSTRAINT `unit_ibfk_3` FOREIGN KEY (`PlayerReplayID`) REFERENCES `playerreplay` (`PlayerReplayID`) ON DELETE CASCADE;

--
-- Constraints for table `unitgroup`
--
ALTER TABLE `unitgroup`
  ADD CONSTRAINT `unitgroup_ibfk_1` FOREIGN KEY (`UnitID`) REFERENCES `unit` (`UnitID`) ON DELETE CASCADE;

--
-- Constraints for table `visibilitychange`
--
ALTER TABLE `visibilitychange`
  ADD CONSTRAINT `visibilitychange_ibfk_3` FOREIGN KEY (`ViewerID`) REFERENCES `playerreplay` (`PlayerReplayID`) ON DELETE CASCADE,
  ADD CONSTRAINT `visibilitychange_ibfk_4` FOREIGN KEY (`UnitID`) REFERENCES `unit` (`UnitID`) ON DELETE CASCADE;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
