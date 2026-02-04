-- Vidal.ru Database Initialization
-- This script is executed when PostgreSQL container starts

-- Create drugs table if not exists (SQLAlchemy will also create it)
-- This is just for documentation and manual setup

-- Enable case-insensitive text search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE vidal TO vidal;

-- Log successful initialization
SELECT 'Vidal database initialized successfully' AS status;
