@echo off
echo Uruchamianie bazy danych (tryb dev)...
docker compose up -d postgres
echo.
echo Baza PostgreSQL uruchomiona.
echo Uruchom recznie:
echo   backend:  cd backend ^&^& mvnw spring-boot:run -Dspring-boot.run.profiles=dev
echo   frontend: cd frontend ^&^& npm run dev
