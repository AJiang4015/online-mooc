/*
 Navicat Premium Dump SQL

 Source Server         : tianji
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : 192.168.150.101:3306
 Source Schema         : tj_unqid

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 15/04/2026 23:40:00
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for cosid
-- ----------------------------
DROP TABLE IF EXISTS `cosid`;
CREATE TABLE `cosid`  (
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '{namespace}.{name}',
  `last_max_id` bigint NOT NULL DEFAULT 0 COMMENT 'last allocated max segment id',
  `last_fetch_time` bigint NOT NULL COMMENT 'last segment fetch timestamp',
  PRIMARY KEY (`name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CosId Segment table' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for cosid_machine
-- ----------------------------
DROP TABLE IF EXISTS `cosid_machine`;
CREATE TABLE `cosid_machine`  (
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '{namespace}.{machine_id}',
  `namespace` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'CosId namespace',
  `machine_id` int NOT NULL DEFAULT 0 COMMENT 'allocated machine id',
  `last_timestamp` bigint NOT NULL DEFAULT 0 COMMENT 'last used timestamp',
  `instance_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT 'service instance id',
  `distribute_time` bigint NOT NULL DEFAULT 0 COMMENT 'distribution time',
  `revert_time` bigint NOT NULL DEFAULT 0 COMMENT 'recycle time',
  PRIMARY KEY (`name`) USING BTREE,
  INDEX `idx_namespace`(`namespace` ASC) USING BTREE,
  INDEX `idx_instance_id`(`instance_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CosId MachineId table' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
