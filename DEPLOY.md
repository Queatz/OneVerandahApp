
## Deploy

First, install the dependencies.

```shell
apt update && apt install nano certbot nodejs npm nginx python3-certbot-nginx
```

Ensure your domain DNS is pointed to the server's IP address.

### Upload the UI

Place the contents of the UI in `/root/ui`.

### HTTP → HTTPS

Replace the contents of `/etc/nginx/sites-enabled/default` with the following.

```
server {
    server_name oneverandah.app;
    root /root/ui;
    gzip on;
    gzip_types text/plain application/javascript;

    listen 80;

    location / {
        index index.html;
        try_files $uri $uri/ /index.html =404;
    }

    # Image backend (see IMAGES.md). Proxies the API and generated images to the
    # Ktor server running on 127.0.0.1:7071, so the frontend can use same-origin
    # URLs in production.
    location /api/ {
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_pass http://127.0.0.1:7071;
    }
    location /images/ {
        proxy_pass http://127.0.0.1:7071;
    }
}
```

For the image backend itself (server, Qwen API key, OSS bucket, and
development/production SSL), follow [IMAGES.md](./IMAGES.md) and use the
`deploy/init.sh` + `deploy/deploy.sh` scripts.

### Finally, configure certbot and nginx

```shell
chmod 755 /root
certbot --nginx
nginx -t
service nginx restart
```
