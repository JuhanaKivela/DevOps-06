FROM nginx:stable-alpine
COPY ./resources /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/nginx.conf
COPY .htpasswd /etc/nginx/.htpasswd
CMD ["nginx", "-g", "daemon off;"]
EXPOSE 8198