-- --------------------------------------------------------
-- Servidor:                     127.0.0.1
-- Versão do servidor:           10.4.32-MariaDB - mariadb.org binary distribution
-- OS do Servidor:               Win64
-- HeidiSQL Versão:              12.10.0.7000
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Copiando estrutura do banco de dados para rehabit
CREATE DATABASE IF NOT EXISTS `rehabit` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */;
USE `rehabit`;

-- Copiando estrutura para tabela rehabit.tb01_clinica
CREATE TABLE IF NOT EXISTS `tb01_clinica` (
  `tb01_id_clinica` int(11) NOT NULL AUTO_INCREMENT,
  `tb01_nome_clinica` varchar(150) NOT NULL,
  `tb01_CNPJ` varchar(18) NOT NULL,
  `tb01_endereco_clinica` varchar(200) DEFAULT NULL,
  `tb01_telefone_clinica` varchar(20) DEFAULT NULL,
  `tb01_email_clinica` varchar(150) NOT NULL,
  `tb01_senha_clinica` varchar(255) NOT NULL,
  `tb01_descricao_clinica` text DEFAULT NULL,
  `tb01_subtitulo` varchar(150) DEFAULT NULL,
  `tb01_foto_clinica` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`tb01_id_clinica`),
  UNIQUE KEY `tb01_CNPJ` (`tb01_CNPJ`),
  UNIQUE KEY `tb01_email_clinica` (`tb01_email_clinica`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb01_clinica: ~0 rows (aproximadamente)
INSERT IGNORE INTO `tb01_clinica` (`tb01_id_clinica`, `tb01_nome_clinica`, `tb01_CNPJ`, `tb01_endereco_clinica`, `tb01_telefone_clinica`, `tb01_email_clinica`, `tb01_senha_clinica`, `tb01_descricao_clinica`, `tb01_subtitulo`, `tb01_foto_clinica`) VALUES
	(1, 'Comando Vermelho', '66666', 'Rua Pintosvaldo', '666666', 'nathan@gmail.com', '$2a$10$ezkmnMxW2XkREFz5WKc3UeHPmxaMfvHbBKdD1qQREvciEzN1KWDHW', 'Merda', 'Nathan', NULL);

-- Copiando estrutura para tabela rehabit.tb02_fisioterapeuta
CREATE TABLE IF NOT EXISTS `tb02_fisioterapeuta` (
  `tb02_id_fisioterapeuta` int(11) NOT NULL AUTO_INCREMENT,
  `tb02_nome_fisioterapeuta` varchar(150) NOT NULL,
  `tb02_COFFITO` varchar(20) NOT NULL,
  `tb02_telefone_fisioterapeuta` varchar(20) DEFAULT NULL,
  `tb02_email_fisioterapeuta` varchar(150) NOT NULL,
  `tb02_senha_fisioterapeuta` varchar(255) NOT NULL,
  `tb02_descricao_fisioterapeuta` text DEFAULT NULL,
  `tb02_especialidade` varchar(100) DEFAULT NULL,
  `tb02_foto_fisioterapeuta` varchar(255) DEFAULT NULL,
  `tb02_id_clinica` int(11) NOT NULL,
  `tb02_localidade` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`tb02_id_fisioterapeuta`),
  UNIQUE KEY `tb02_COFFITO` (`tb02_COFFITO`),
  UNIQUE KEY `tb02_email_fisioterapeuta` (`tb02_email_fisioterapeuta`),
  KEY `idx_tb02_id_clinica` (`tb02_id_clinica`),
  CONSTRAINT `fk_tb02_tb01` FOREIGN KEY (`tb02_id_clinica`) REFERENCES `tb01_clinica` (`tb01_id_clinica`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb02_fisioterapeuta: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb03_paciente
CREATE TABLE IF NOT EXISTS `tb03_paciente` (
  `tb03_id_paciente` int(11) NOT NULL AUTO_INCREMENT,
  `tb03_nome_paciente` varchar(150) NOT NULL,
  `tb03_CPF` varchar(14) NOT NULL,
  `tb03_telefone_paciente` varchar(20) DEFAULT NULL,
  `tb03_email_paciente` varchar(150) DEFAULT NULL,
  `tb03_data_nascimento` date DEFAULT NULL,
  `tb03_sexo` varchar(20) DEFAULT NULL,
  `tb03_data_inicio_tratamento` date DEFAULT NULL,
  `tb03_situacao` varchar(50) DEFAULT NULL,
  `tb03_status` varchar(50) DEFAULT NULL,
  `tb03_id_clinica` int(11) NOT NULL,
  `tb03_id_fisioterapeuta` int(11) NOT NULL,
  PRIMARY KEY (`tb03_id_paciente`),
  UNIQUE KEY `tb03_CPF` (`tb03_CPF`),
  KEY `idx_tb03_id_clinica` (`tb03_id_clinica`),
  KEY `idx_tb03_id_fisioterapeuta` (`tb03_id_fisioterapeuta`),
  CONSTRAINT `fk_tb03_tb01` FOREIGN KEY (`tb03_id_clinica`) REFERENCES `tb01_clinica` (`tb01_id_clinica`) ON UPDATE CASCADE,
  CONSTRAINT `fk_tb03_tb02` FOREIGN KEY (`tb03_id_fisioterapeuta`) REFERENCES `tb02_fisioterapeuta` (`tb02_id_fisioterapeuta`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb03_paciente: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb04_goniometro
CREATE TABLE IF NOT EXISTS `tb04_goniometro` (
  `tb04_id_goniometro` int(11) NOT NULL AUTO_INCREMENT,
  `tb04_bateria` int(11) DEFAULT NULL,
  `tb04_data_sincronizacao` date DEFAULT NULL,
  `tb04_hora_sincronizacao` time DEFAULT NULL,
  `tb04_id_clinica` int(11) NOT NULL,
  PRIMARY KEY (`tb04_id_goniometro`),
  KEY `idx_tb04_id_clinica` (`tb04_id_clinica`),
  CONSTRAINT `fk_tb04_tb01` FOREIGN KEY (`tb04_id_clinica`) REFERENCES `tb01_clinica` (`tb01_id_clinica`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb04_goniometro: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb05_sessoes
CREATE TABLE IF NOT EXISTS `tb05_sessoes` (
  `tb05_id_sessoes` int(11) NOT NULL AUTO_INCREMENT,
  `tb05_duracao` int(11) DEFAULT NULL,
  `tb05_data_sessoes` date DEFAULT NULL,
  `tb05_hora_sessoes` time DEFAULT NULL,
  `tb05_id_fisioterapeuta` int(11) NOT NULL,
  `tb05_id_paciente` int(11) NOT NULL,
  PRIMARY KEY (`tb05_id_sessoes`),
  KEY `idx_tb05_id_fisioterapeuta` (`tb05_id_fisioterapeuta`),
  KEY `idx_tb05_id_paciente` (`tb05_id_paciente`),
  CONSTRAINT `fk_tb05_tb02` FOREIGN KEY (`tb05_id_fisioterapeuta`) REFERENCES `tb02_fisioterapeuta` (`tb02_id_fisioterapeuta`) ON UPDATE CASCADE,
  CONSTRAINT `fk_tb05_tb03` FOREIGN KEY (`tb05_id_paciente`) REFERENCES `tb03_paciente` (`tb03_id_paciente`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb05_sessoes: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb06_medicao
CREATE TABLE IF NOT EXISTS `tb06_medicao` (
  `tb06_id_medicao` int(11) NOT NULL AUTO_INCREMENT,
  `tb06_amplitude_media` decimal(6,2) DEFAULT NULL,
  `tb06_data_medicao` date DEFAULT NULL,
  `tb06_hora_medicao` time DEFAULT NULL,
  `tb06_id_sessoes` int(11) NOT NULL,
  PRIMARY KEY (`tb06_id_medicao`),
  KEY `idx_tb06_id_sessoes` (`tb06_id_sessoes`),
  CONSTRAINT `fk_tb06_tb05` FOREIGN KEY (`tb06_id_sessoes`) REFERENCES `tb05_sessoes` (`tb05_id_sessoes`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb06_medicao: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb07_agendamento
CREATE TABLE IF NOT EXISTS `tb07_agendamento` (
  `tb07_id_agendamento` int(11) NOT NULL AUTO_INCREMENT,
  `tb07_data_agendamento` date NOT NULL,
  `tb07_hora_agendamento` time NOT NULL,
  `tb07_observacao` varchar(255) DEFAULT NULL,
  `tb07_id_fisioterapeuta` int(11) NOT NULL,
  `tb07_id_paciente` int(11) NOT NULL,
  PRIMARY KEY (`tb07_id_agendamento`),
  KEY `idx_tb07_id_fisioterapeuta` (`tb07_id_fisioterapeuta`),
  KEY `idx_tb07_id_paciente` (`tb07_id_paciente`),
  CONSTRAINT `fk_tb07_tb02` FOREIGN KEY (`tb07_id_fisioterapeuta`) REFERENCES `tb02_fisioterapeuta` (`tb02_id_fisioterapeuta`) ON UPDATE CASCADE,
  CONSTRAINT `fk_tb07_tb03` FOREIGN KEY (`tb07_id_paciente`) REFERENCES `tb03_paciente` (`tb03_id_paciente`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb07_agendamento: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb11_recuperacao_senha
CREATE TABLE IF NOT EXISTS `tb11_recuperacao_senha` (
  `tb11_id_recuperacao` int(11) NOT NULL AUTO_INCREMENT,
  `tb11_email` varchar(150) NOT NULL,
  `tb11_tipo_conta` varchar(20) NOT NULL,
  `tb11_token_hash` varchar(64) NOT NULL,
  `tb11_codigo_hash` varchar(64) NOT NULL,
  `tb11_tentativas` int(11) NOT NULL DEFAULT 0,
  `tb11_criado_em` datetime NOT NULL,
  `tb11_expira_em` datetime NOT NULL,
  `tb11_usado_em` datetime DEFAULT NULL,
  PRIMARY KEY (`tb11_id_recuperacao`),
  UNIQUE KEY `tb11_token_hash` (`tb11_token_hash`),
  KEY `idx_tb11_email` (`tb11_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb11_recuperacao_senha: ~0 rows (aproximadamente)

-- Copiando estrutura para tabela rehabit.tb12_verificacao_email
CREATE TABLE IF NOT EXISTS `tb12_verificacao_email` (
  `tb12_id_verificacao` int(11) NOT NULL AUTO_INCREMENT,
  `tb12_email` varchar(150) NOT NULL,
  `tb12_codigo_hash` varchar(64) NOT NULL,
  `tb12_tentativas` int(11) NOT NULL DEFAULT 0,
  `tb12_criado_em` datetime NOT NULL,
  `tb12_expira_em` datetime NOT NULL,
  `tb12_verificado_em` datetime DEFAULT NULL,
  PRIMARY KEY (`tb12_id_verificacao`),
  UNIQUE KEY `tb12_email` (`tb12_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Copiando dados para a tabela rehabit.tb12_verificacao_email: ~0 rows (aproximadamente)

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
