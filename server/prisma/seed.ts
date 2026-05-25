import bcrypt from "bcrypt";
import prisma from "../src/utils/prisma";

async function seed(): Promise<void> {
  const passwordHash = await bcrypt.hash("admin123", 12);

  await prisma.adminUser.upsert({
    where: { username: "admin" },
    update: {},
    create: {
      username: "admin",
      passwordHash,
      displayName: "Super Admin",
      role: "superadmin",
    },
  });

  console.log("Seed complete: superadmin account created (username: admin, password: admin123)");
}

seed()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
