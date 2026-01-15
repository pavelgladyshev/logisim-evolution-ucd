#pragma once

#ifndef CONSOLE_H


#define CONSOLE_H

#define DEV_CONSOLE 1


struct dev_ops;

void console_init(void);
int dev_register(int major, struct dev_ops *ops);

#endif