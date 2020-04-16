-- Run this script to set up IPFS database

CREATE TABLE ipfs_map (
  chunk_hash VARCHAR(255) PRIMARY KEY,
  ipfs_file VARCHAR(255),
  crypto_key VARCHAR(255)
);
