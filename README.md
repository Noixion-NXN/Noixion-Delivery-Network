# Noixion Delivery Network
Storage and Content Delivery Network for [Noixion TV](https://noixion.tv)

## Requeriments
- ffmpeg
- ffprobe

You can use this project to connect your storage to Noixion TV.
 
Copy application.example.conf to application.conf and change it with your server configuration.


Register your network at [Noixion TV](https://noixion.tv/account/networks/register)
## Chunk storage configuration
- Choose 'kademlia' for DHT storage.
- Choose 's3' to use Amazon S3 storage.
- Choose 'ipfs' to use IPFS (Inter-Planetary file system)
- Choose 'btfs' to use BTFS (BitTorrent file system)