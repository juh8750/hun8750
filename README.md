# Equipment and License Management

This project provides a minimal command line tool for tracking equipment, licenses,
contracts and maintenance requests. Data is stored in a local SQLite database
(`equipment.db`).

## Usage

```bash
# Add new equipment
python3 src/main.py add_equipment --name "Router" --manufacturer "ACME" \
  --purchase_date 2020-01-10 --install_date 2020-01-12 \
  --maintenance_expiration 2025-01-10 --lifespan 5

# Add a license for the equipment with id 1
python3 src/main.py add_license --equipment_id 1 --type "Firewall" \
  --issue_date 2023-01-01 --expiration_date 2024-01-01 --key ABC123

# Check for upcoming replacements (within 90 days)
python3 src/main.py check_replacements

# Check for licenses expiring in the next 30 days
python3 src/main.py check_licenses
```
